package io.taucoin.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import io.taucoin.net.rlpx.NodeType;
import io.taucoin.core.Genesis;
import io.taucoin.core.genesis.GenesisLoader;
import io.taucoin.crypto.ECKey;
import io.taucoin.net.p2p.P2pHandler;
import io.taucoin.net.rlpx.MessageCodec;
import io.taucoin.net.rlpx.Node;
import io.taucoin.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.*;

import static io.taucoin.crypto.SHA3Helper.sha3;

/**
 * Utility class to retrieve property values from the ethereumj.conf files
 *
 * The properties are taken from different sources and merged in the following order
 * (the config option from the next source overrides option from previous):
 * - resource ethereumj.conf : normally used as a reference config with default values
 *          and shouldn't be changed
 * - system property : each config entry might be altered via -D VM option
 * - [user dir]/config/ethereumj.conf
 * - config specified with the -Dethereumj.conf.file=[file.conf] VM option
 * - CLI options
 *
 * @author Roman Mandeleil
 * @since 22.05.2014
 */
public class SystemProperties {
    private static Logger logger = LoggerFactory.getLogger("general");

    public final static String PROPERTY_DB_DIR = "database.dir";
    public final static String PROPERTY_LISTEN_PORT = "peer.listen.port";
    public final static String PROPERTY_PEER_ACTIVE = "peer.active";
    public final static String PROPERTY_DB_RESET = "database.reset";
    public final static int PEER_NUMBER = 3;

    /* Testing */
    private final static Boolean DEFAULT_VMTEST_LOAD_LOCAL = false;
    private final static String DEFAULT_BLOCKS_LOADER = "";

    public final static SystemProperties CONFIG = new SystemProperties();

    /**
     * Marks config accessor methods which need to be called (for value validation)
     * upon config creation or modification
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ValidateMe {};


    private Config config;

    // mutable options for tests
    private String databaseDir = null;
    private Boolean databaseReset = null;
    private String projectVersion = null;
    private String projectVersionModifier = null;

    private String genesisInfo = null;

    private String bindIp = null;
    private String externalIp = null;

    private Boolean syncEnabled = null;
    private Boolean discoveryEnabled = null;
    private Boolean rpcEnabled = null;

    private Genesis genesis;

    // Forger private key, public key and coinbase management.
    private byte[] forgerPrivateKey = null;
    private byte[] forgerPublicKey = null;
    private byte[] forgerCoinbase = null;

    private Object forgerKeyLock = new Object();

    public SystemProperties() {
        this(ConfigFactory.empty());
    }

    public SystemProperties(File configFile) {
        this(ConfigFactory.parseFile(configFile));
    }

    public SystemProperties(String configResource) {
        this(ConfigFactory.parseResources(configResource));
    }

    public SystemProperties(Config apiConfig) {
        try {
            Config javaSystemProperties = ConfigFactory.load("no-such-resource-only-system-props");
            Config referenceConfig = ConfigFactory.parseResources("ethereumj.conf");
            logger.info("Config (" + (referenceConfig.entrySet().size() > 0 ? " yes " : " no  ") + "): default properties from resource 'ethereumj.conf'");
            Config userConfig = ConfigFactory.parseResources("user.conf");
            logger.info("Config (" + (userConfig.entrySet().size() > 0 ? " yes " : " no  ") + "): user properties from resource 'user.conf'");
            File userDirFile = new File(System.getProperty("user.dir"), "/config/ethereumj.conf");
            Config userDirConfig = ConfigFactory.parseFile(userDirFile);
            logger.info("Config (" + (userDirConfig.entrySet().size() > 0 ? " yes " : " no  ") + "): user properties from file '" + userDirFile + "'");
            Config testConfig = ConfigFactory.parseResources("test-ethereumj.conf");
            logger.info("Config (" + (testConfig.entrySet().size() > 0 ? " yes " : " no  ") + "): test properties from resource 'test-ethereumj.conf'");
            Config testUserConfig = ConfigFactory.parseResources("test-user.conf");
            logger.info("Config (" + (testUserConfig.entrySet().size() > 0 ? " yes " : " no  ") + "): test properties from resource 'test-user.conf'");
            String file = System.getProperty("ethereumj.conf.file");
            Config cmdLineConfig = file != null ? ConfigFactory.parseFile(new File(file)) :
                    ConfigFactory.empty();
            logger.info("Config (" + (cmdLineConfig.entrySet().size() > 0 ? " yes " : " no  ") + "): user properties from -Dethereumj.conf.file file '" + file + "'");
            logger.info("Config (" + (apiConfig.entrySet().size() > 0 ? " yes " : " no  ") + "): config passed via constructor");
            config = javaSystemProperties
                    .withFallback(apiConfig)
                    .withFallback(cmdLineConfig)
                    .withFallback(testUserConfig)
                    .withFallback(testConfig)
                    .withFallback(userConfig)
                    .withFallback(userDirConfig)
                    .withFallback(referenceConfig);
            validateConfig();

            logger.debug("Config trace: " + config.root().render(ConfigRenderOptions.defaults().
                    setComments(false).setJson(false)));

            Properties props = new Properties();
            InputStream is = getClass().getResourceAsStream("/version.properties");
            props.load(is);
            this.projectVersion = props.getProperty("versionNumber");
            this.projectVersion = this.projectVersion.replaceAll("'", "");

            if (this.projectVersion == null) this.projectVersion = "-.-.-";

            this.projectVersionModifier = props.getProperty("modifier");
            this.projectVersionModifier = this.projectVersionModifier.replaceAll("\"", "");

        } catch (Exception e) {
            logger.error("Can't read config.", e);
            throw new RuntimeException(e);
        }
    }

    public Config getConfig() {
        return config;
    }

    /**
     * Puts a new config atop of existing stack making the options
     * in the supplied config overriding existing options
     * Once put this config can't be removed
     *
     * @param overrideOptions - atop config
     */
    public void overrideParams(Config overrideOptions) {
        config = overrideOptions.withFallback(config);
        validateConfig();
    }

    /**
     * Puts a new config atop of existing stack making the options
     * in the supplied config overriding existing options
     * Once put this config can't be removed
     *
     * @param keyValuePairs [name] [value] [name] [value] ...
     */
    public void overrideParams(String ... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) throw new RuntimeException("Odd argument number");
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        overrideParams(map);
    }

    /**
     * Puts a new config atop of existing stack making the options
     * in the supplied config overriding existing options
     * Once put this config can't be removed
     *
     * @param cliOptions -  command line options to take presidency
     */
    public void overrideParams(Map<String, String> cliOptions) {
        Config cliConf = ConfigFactory.parseMap(cliOptions);
        overrideParams(cliConf);
    }

    private void validateConfig() {
        for (Method method : getClass().getMethods()) {
            try {
                if (method.isAnnotationPresent(ValidateMe.class)) {
                    method.invoke(this);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error validating config method: " + method, e);
            }
        }
    }

    public <T> T getProperty(String propName, T defaultValue) {
        if (!config.hasPath(propName)) return defaultValue;
        String string = config.getString(propName);
        if (string.trim().isEmpty()) return defaultValue;
        return (T) config.getAnyRef(propName);
    }

    @ValidateMe
    public boolean peerDiscovery() {
        return discoveryEnabled == null ? config.getBoolean("peer.discovery.enabled") : discoveryEnabled;
    }

    public void setDiscoveryEnabled(Boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }

    @ValidateMe
    public boolean peerDiscoveryPersist() {
        return config.getBoolean("peer.discovery.persist");
    }

    @ValidateMe
    public boolean peerConnectionTestEnabled() {
        return config.getBoolean("peer.discovery.connection.test");
    }

    @ValidateMe
    public int peerDiscoveryWorkers() {
        return config.getInt("peer.discovery.workers");
    }

    @ValidateMe
    public int peerDiscoveryTouchPeriod() {
        return config.getInt("peer.discovery.touchPeriod");
    }

    @ValidateMe
    public int peerDiscoveryTouchMaxNodes() {
        return config.getInt("peer.discovery.touchMaxNodes");
    }

    @ValidateMe
    public int peerConnectionTimeout() {
        return config.getInt("peer.connection.timeout") * 1000;
    }

    @ValidateMe
    public int defaultP2PVersion() {
        return config.hasPath("peer.p2p.version") ? config.getInt("peer.p2p.version") : P2pHandler.VERSION;
    }

    @ValidateMe
    public int rlpxMaxFrameSize() {
        return config.hasPath("peer.p2p.framing.maxSize") ? config.getInt("peer.p2p.framing.maxSize") : MessageCodec.NO_FRAMING;
    }


    @ValidateMe
    public int transactionApproveTimeout() {
        return config.getInt("transaction.approve.timeout") * 1000;
    }

    @ValidateMe
    public List<String> peerDiscoveryIPList() {
        return config.getStringList("peer.discovery.ip.list");
    }

    @ValidateMe
    public boolean databaseReset() {
        return databaseReset == null ? config.getBoolean("database.reset") : databaseReset;
    }

    public void setDatabaseReset(Boolean reset) {
        databaseReset = reset;
    }

    @ValidateMe
    public List<Node> peerActive() {
        if (!config.hasPath("peer.active")) {
            return Collections.EMPTY_LIST;
        }
        List<Node> ret = new ArrayList<>();
        List<? extends ConfigObject> list = config.getObjectList("peer.active");
        for (ConfigObject configObject : list) {
            Node n;
            if (configObject.get("url") != null) {
                String url = configObject.toConfig().getString("url");
                n = new Node(url.startsWith("enode://") ? url : "enode://" + url);
                n.setType(NodeType.SUPER);
            } else if (configObject.get("ip") != null) {
                String ip = configObject.toConfig().getString("ip");
                int port = configObject.toConfig().getInt("port");
                byte[] nodeId;
                if (configObject.toConfig().hasPath("nodeId")) {
                    nodeId = Hex.decode(configObject.toConfig().getString("nodeId").trim());
                    if (nodeId.length != 64) {
                        throw new RuntimeException("Invalid config nodeId '" + nodeId + "' at " + configObject);
                    }
                } else {
                    if (configObject.toConfig().hasPath("nodeName")) {
                        String nodeName = configObject.toConfig().getString("nodeName").trim();
                        // FIXME should be sha3-512 here ?
                        nodeId = ECKey.fromPrivate(sha3(nodeName.getBytes())).getNodeId();
                    } else {
                        throw new RuntimeException("Either nodeId or nodeName should be specified: " + configObject);
                    }
                }
                n = new Node(nodeId, ip, port, NodeType.SUPER);
            } else {
                throw new RuntimeException("Unexpected element within 'peer.active' config list: " + configObject);
            }
            ret.add(n);
        }
        return ret;
    }

    @ValidateMe
    public NodeFilter peerTrusted() {
        List<? extends ConfigObject> list = config.getObjectList("peer.trusted");
        NodeFilter ret = new NodeFilter();

        for (ConfigObject configObject : list) {
            byte[] nodeId = null;
            String ipMask = null;
            if (configObject.get("nodeId") != null) {
                nodeId = Hex.decode(configObject.toConfig().getString("nodeId").trim());
            }
            if (configObject.get("ip") != null) {
                ipMask = configObject.toConfig().getString("ip").trim();
            }
            ret.add(nodeId, ipMask);
        }
        return ret;
    }


    public String samplesDir() {
        return config.getString("samples.dir");
    }

    @ValidateMe
    public String coinbaseSecret() {
        return config.getString("coinbase.secret");
    }

    @ValidateMe
    public Integer peerChannelReadTimeout() {
        return config.getInt("peer.channel.read.timeout");
    }

    @ValidateMe
    public Integer traceStartBlock() {
        return config.getInt("trace.startblock");
    }

    @ValidateMe
    public boolean recordBlocks() {
        return config.getBoolean("record.blocks");
    }

    @ValidateMe
    public boolean dumpFull() {
        return config.getBoolean("dump.full");
    }

    @ValidateMe
    public String dumpDir() {
        return config.getString("dump.dir");
    }

    @ValidateMe
    public String dumpStyle() {
        return config.getString("dump.style");
    }

    @ValidateMe
    public int dumpBlock() {
        return config.getInt("dump.block");
    }

    @ValidateMe
    public String databaseDir() {
        return databaseDir == null ? config.getString("database.dir") : databaseDir;
    }

    public void setDataBaseDir(String dataBaseDir) {
        this.databaseDir = dataBaseDir;
    }

    @ValidateMe
    public boolean dumpCleanOnRestart() {
        return config.getBoolean("dump.clean.on.restart");
    }

    @ValidateMe
    public boolean playVM() {
        return config.getBoolean("play.vm");
    }

    @ValidateMe
    public boolean blockChainOnly() {
        return config.getBoolean("blockchain.only");
    }

    @ValidateMe
    public int maxHashesAsk() {
        return config.getInt("sync.max.hashes.ask");
    }

    @ValidateMe
    public int maxBlocksAsk() {
        return config.getInt("sync.max.blocks.ask");
    }

    @ValidateMe
    public int syncPeerCount() {
        return config.getInt("sync.peer.count");
    }

    public Integer syncVersion() {
        if (!config.hasPath("sync.version")) {
            return null;
        }
        return config.getInt("sync.version");
    }

    @ValidateMe
    public String projectVersion() {
        return projectVersion;
    }

    @ValidateMe
    public String projectVersionModifier() {
        return projectVersionModifier;
    }

    @ValidateMe
    public String helloPhrase() {
        return config.getString("hello.phrase");
    }

    @ValidateMe
    public List<String> peerCapabilities() {
        return config.getStringList("peer.capabilities");
    }

    @ValidateMe
    public boolean vmTrace() {
        return config.getBoolean("vm.structured.trace");
    }

    @ValidateMe
    public boolean vmTraceCompressed() {
        return config.getBoolean("vm.structured.compressed");
    }

    @ValidateMe
    public int vmTraceInitStorageLimit() {
        return config.getInt("vm.structured.initStorageLimit");
    }

    @ValidateMe
    public int detailsInMemoryStorageLimit() {
        return config.getInt("details.inmemory.storage.limit");
    }

    @ValidateMe
    public double cacheFlushMemory() {
        return config.getDouble("cache.flush.memory");
    }

    @ValidateMe
    public int cacheFlushBlocks() {
        return config.getInt("cache.flush.blocks");
    }

    @ValidateMe
    public String vmTraceDir() {
        return config.getString("vm.structured.dir");
    }

    @ValidateMe
    public String privateKey() {
        if (forgerPrivateKey != null) {
            return Hex.toHexString(forgerPrivateKey);
        } else if (config.hasPath("peer.privateKey")) {
            String key = config.getString("peer.privateKey");
            if (key.length() != 64) {
                throw new RuntimeException("The peer.privateKey needs to be Hex encoded and 32 byte length");
            }
            return key;
        } else {
            return getGeneratedNodePrivateKey();
        }
    }

    private String getGeneratedNodePrivateKey() {
        try {
            File file = new File(databaseDir(), "nodeId.properties");
            Properties props = new Properties();
            if (file.canRead()) {
                props.load(new FileReader(file));
            } else {
                ECKey key = new ECKey().decompress();
                props.setProperty("nodeIdPrivateKey", Hex.toHexString(key.getPrivKeyBytes()));
                props.setProperty("nodeId", Hex.toHexString(key.getNodeId()));
                file.getParentFile().mkdirs();
                props.store(new FileWriter(file), "Generated NodeID. To use your own nodeId please refer to 'peer.privateKey' config option.");
                logger.info("New nodeID generated: " + props.getProperty("nodeId"));
                logger.info("Generated nodeID and its private key stored in " + file);
            }
            return props.getProperty("nodeIdPrivateKey");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ValidateMe
    public ECKey getMyKey() {
        return ECKey.fromPrivate(Hex.decode(privateKey())).decompress();
    }

    /**
     *  Home NodeID calculated from 'peer.privateKey' property
     */
    @ValidateMe
    public byte[] nodeId() {
        return getMyKey().getNodeId();
    }

    @ValidateMe
    public int networkId() {
        return config.getInt("peer.networkId");
    }

    @ValidateMe
    public int maxActivePeers() {
        return config.getInt("peer.maxAcivePeers");
    }

    @ValidateMe
    public int listenPort() {
        return config.getInt("peer.listen.port");
    }


    /**
     * This can be a blocking call with long timeout (thus no ValidateMe)
     */
    public String bindIp() {
        if (!config.hasPath("peer.discovery.bind.ip") || config.getString("peer.discovery.bind.ip").trim().isEmpty()) {
            if (bindIp == null) {
                logger.info("Bind address wasn't set, Punching to identify it...");
                try {
                    Socket s = new Socket("www.google.com", 80);
                    bindIp = s.getLocalAddress().getHostAddress();
                    logger.info("UDP local bound to: {}", bindIp);
                } catch (IOException e) {
                    logger.warn("Can't get bind IP. Fall back to 0.0.0.0: " + e);
                    bindIp = "0.0.0.0";
                }
            }
            return bindIp;
        } else {
            return config.getString("peer.discovery.bind.ip").trim();
        }
    }

    /**
     * This can be a blocking call with long timeout (thus no ValidateMe)
     */
    public String externalIp() {
        if (!config.hasPath("peer.discovery.external.ip") || config.getString("peer.discovery.external.ip").trim().isEmpty()) {
            if (externalIp == null) {
                logger.info("External IP wasn't set, using checkip.amazonaws.com to identify it...");
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            new URL("http://checkip.amazonaws.com").openStream()));
                    externalIp = in.readLine();
                    if (externalIp == null || externalIp.trim().isEmpty()) {
                        throw new IOException("Invalid address: '" + externalIp + "'");
                    }
                    try {
                        InetAddress.getByName(externalIp);
                    } catch (Exception e) {
                        throw new IOException("Invalid address: '" + externalIp + "'");
                    }
                    logger.info("External address identified: {}", externalIp);
                } catch (IOException e) {
                    externalIp = bindIp();
                    logger.warn("Can't get external IP. Fall back to peer.bind.ip: " + externalIp + " :" + e);
                }
            }
            return externalIp;

        } else {
            return config.getString("peer.discovery.external.ip").trim();
        }
    }

    @ValidateMe
    public String getKeyValueDataSource() {
        return config.getString("keyvalue.datasource");
    }

    @ValidateMe
    public boolean isRedisEnabled() {
        return config.getBoolean("redis.enabled");
    }

    @ValidateMe
    public boolean isSyncEnabled() {
        return this.syncEnabled == null ? config.getBoolean("sync.enabled") : syncEnabled;
    }

    public void setSyncEnabled(Boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    @ValidateMe
    public boolean isRpcEnabled() {
        return this.rpcEnabled == null ? config.getBoolean("rpc.enabled") : rpcEnabled;
    }

    public void setRpcEnabled(Boolean rpcEnabled) {
        this.rpcEnabled = rpcEnabled;
    }

    @ValidateMe
    public int rpcListenPort() {
        return config.getInt("rpc.listen.port");
    }

    @ValidateMe
    public String getRpcServerType() {
        return config.getString("rpc.server.type");
    }

    @ValidateMe
    public boolean isPublicHomeNode() { return config.getBoolean("peer.discovery.public.home.node");}

    @ValidateMe
    public NodeType getHomeNodeType() {
        String homeNodeType = config.getString("peer.discovery.home.node.type");
        return "super".equals(homeNodeType) ? NodeType.SUPER : NodeType.MOBILE;
    }

    @ValidateMe
    public boolean isStorageDictionaryEnabled() { return config.getBoolean("vm.structured.storage.dictionary.enabled");}


    @ValidateMe
    public String genesisInfo() {

        if (genesisInfo == null)
            return config.getString("genesis");
        else
            return genesisInfo;
    }

    public boolean isFrontier() {
        return genesisInfo().contains("frontier");
    }

    @ValidateMe
    public int txOutdatedThreshold() {
        return config.getInt("transaction.outdated.threshold");
    }

    public void setGenesisInfo(String genesisInfo){
        this.genesisInfo = genesisInfo;
    }

    @ValidateMe
    public int getMutableRange() {
        return config.getInt("mutable.range");
    }

    @ValidateMe
    public boolean forgerStart() {
        return config.getBoolean("forge.start");
    }

    @ValidateMe
    public int getForgedAmount() {
        return config.getInt("forge.forgedAmount");
    }

    public void importForgerPrikey(byte[] prikey) {
        synchronized(forgerKeyLock) {
            if (prikey == null) {
                forgerPrivateKey = null;
                forgerPublicKey = null;
                forgerCoinbase = null;
            } else {
                forgerPrivateKey = prikey;
                forgerPublicKey = ECKey.fromPrivate(prikey).getPubKey();
                forgerCoinbase = ECKey.fromPrivate(prikey).getAddress();
            }
        }
    }

    @ValidateMe
    public byte[] getForgerPrikey() {
        synchronized(forgerKeyLock) {
            if (forgerPrivateKey != null) {
                return forgerPrivateKey;
            }

            String privkeyStr = config.getString("forge.prikey");
            forgerPrivateKey = Utils.getRawPrivateKeyString(privkeyStr);
            forgerPublicKey = ECKey.fromPrivate(forgerPrivateKey).getPubKey();
            forgerCoinbase = ECKey.fromPrivate(forgerPrivateKey).getAddress();

            //logger.info("forger privkey {}", Hex.toHexString(forgerPrivateKey));
            logger.info("forger pubkey {}", Hex.toHexString(forgerPublicKey));
            logger.info("forger coinbase {}", Hex.toHexString(forgerCoinbase));

            return forgerPrivateKey;
        }
    }

    public byte[] getForgerPubkey() {
        synchronized(forgerKeyLock) {
            if (forgerPublicKey != null) {
                return forgerPublicKey;
            }

            getForgerPrikey();
            return forgerPublicKey;
        }
    }

    public byte[] getForgerCoinbase() {
        synchronized(forgerKeyLock) {
            if (forgerCoinbase != null) {
                return forgerCoinbase;
            }

            getForgerPrikey();
            return forgerCoinbase;
        }
    }

    public Genesis getGenesis() {
        if (genesis == null) {
            //temporary schema
            genesis = GenesisLoader.loadGenesis(this);
        }
        return genesis;
    }

    @ValidateMe
    public List<String> httpDiscoveryActivePeers() {
        return config.getStringList("http.active.peers");
    }

    @ValidateMe
    public int httpClientPoolSize() {
        return config.getInt("http.client.pool.size");
    }

    @ValidateMe
    public int httpConnectionTimeout() {
        return config.getInt("http.client.connection.timeout") * 1000;
    }

    @ValidateMe
    public int httpConnectionReadTimeout() {
        return config.getInt("http.client.channel.read.timeout");
    }

    @ValidateMe
    public long pullChainInfoPeriod() {
        return config.getInt("sync2.chaininfo.pull.period") * 1000;
    }

    @ValidateMe
    public long pullPoolTxsTime() {
        return config.getInt("sync2.pooltxs.pull.seconds");
    }

    @ValidateMe
    public long pullPoolTxsAmount() {
        return config.getInt("sync2.pooltxs.amount");
    }

    @ValidateMe
    public long pullPoolTxsMinFee() {
        return config.getInt("sync2.pooltxs.minfee");
    }

    @ValidateMe
    public int blockStoreFileMaxSize() {
        return (int)(config.getInt("filestore.blockstore.file.maxsize")
                * 1024 * 1024);
    }

    @ValidateMe
    public int indexStoreFileMetaMaxAmount() {
        return (int)config.getInt("filestore.indexstore.file.maxamount");
    }

    public String dump() {
        return config.root().render(ConfigRenderOptions.defaults().setComments(false));
    }

    /*
     *
     * Testing
     *
     */
    public boolean vmTestLoadLocal() {
        return config.hasPath("GitHubTests.VMTest.loadLocal") ?
                config.getBoolean("GitHubTests.VMTest.loadLocal") : DEFAULT_VMTEST_LOAD_LOCAL;
    }

    public String blocksLoader() {
        return config.hasPath("blocks.loader") ?
                config.getString("blocks.loader") : DEFAULT_BLOCKS_LOADER;
    }
}
