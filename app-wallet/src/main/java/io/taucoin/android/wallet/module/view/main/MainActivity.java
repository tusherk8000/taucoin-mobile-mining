package io.taucoin.android.wallet.module.view.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.widget.CompoundButton;
import android.widget.RadioButton;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.taucoin.android.wallet.R;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.taucoin.android.wallet.MyApplication;
import io.taucoin.android.wallet.base.BaseActivity;
import io.taucoin.android.wallet.module.service.DaemonJobService;
import io.taucoin.android.wallet.module.service.TxService;
import io.taucoin.android.wallet.module.service.UpgradeService;
import io.taucoin.android.wallet.module.view.main.iview.IMainView;
import io.taucoin.android.wallet.net.callback.CommonObserver;
import io.taucoin.android.wallet.util.ProgressManager;
import io.taucoin.android.wallet.util.ToastUtils;
import io.taucoin.foundation.util.ActivityManager;
import io.taucoin.foundation.util.AppUtil;
import io.taucoin.foundation.util.DrawablesUtil;

public class MainActivity extends BaseActivity implements IMainView {
    @BindView(R.id.rb_home)
    RadioButton rbHome;
    @BindView(R.id.rb_send_receive)
    RadioButton rbSendReceive;
    @BindView(R.id.rb_manager)
    RadioButton rbManager;

    private Fragment[] mFragments = new Fragment[3];
    private Subject<Integer> mBackClick = PublishSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ac_main);
        ButterKnife.bind(this);
        initBottomTabView();
        changeTab(0);
        initExitApp();
        UpgradeService.startUpdateService();

    }

    @Override
    public void initBottomTabView() {
        DrawablesUtil.setTopDrawable(rbHome, R.drawable.selector_tab_home,24);
        DrawablesUtil.setTopDrawable(rbSendReceive, R.drawable.selector_tab_send, 24);
        DrawablesUtil.setTopDrawable(rbManager, R.drawable.selector_tab_manage, 24);
    }

    @Override
    public void changeTab(int tabIndex) {
        Fragment fragment = null;
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        if(null == mFragments[tabIndex]){
            if(tabIndex == 0){
                fragment = new HomeFragment();
            }else if(tabIndex == 1){
                fragment = new SendReceiveFragment();
            }else if(tabIndex == 2){
                fragment = new ManageFragment();
            }
            if(fragment != null){
                fragmentTransaction.add(R.id.tab_container, fragment);
                mFragments[tabIndex] = fragment;
            }
        }else{
            fragment = mFragments[tabIndex];
        }
        if(null != fragment){
            hideFragment(fragmentTransaction);
            fragmentTransaction.show(fragment);
        }
        fragmentTransaction.commitAllowingStateLoss();
    }

    private void hideFragment(FragmentTransaction fragmentTransaction) {
        for (Fragment fragment : mFragments) {
            if (fragment != null) {
                fragmentTransaction.hide(fragment);
            }
        }
    }

    @OnCheckedChanged({R.id.rb_home, R.id.rb_manager, R.id.rb_send_receive})
    public void onRadioCheck(CompoundButton view, boolean isChanged) {
        switch (view.getId()) {
            case R.id.rb_home:
                if (isChanged) {
                    changeTab(0);
                }
                break;
            case R.id.rb_send_receive:
                if (isChanged) {
                    changeTab(1);
                }
                break;
            case R.id.rb_manager:
                if (isChanged) {
                    changeTab(2);
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        mBackClick.onNext(1);
    }

    @SuppressLint("CheckResult")
    private void initExitApp() {
        mBackClick.mergeWith(mBackClick.debounce(2000, TimeUnit.MILLISECONDS)
            .map(i -> 0))
            .scan((prev, cur) -> {
                if (cur == 0) return 0;
                return prev + 1;
            })
            .filter(v -> v > 0)
            .subscribe(v -> {
                if (v == 1) {
                    ToastUtils.showLongToast(R.string.main_exit);
                } else if (v == 2) {
                    ActivityManager.getInstance().finishAll();
                }
            });
    }

    private void appExit(){
        ProgressManager.closeProgressDialog();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            DaemonJobService.closeJob(this);
        }
        TxService.stopService();
        UpgradeService.stopUpdateService();
        MyApplication.getRemoteConnector().cancelRemoteConnector();

        Observable.timer(100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new CommonObserver<Long>() {

                @Override
                public void onComplete() {
                    AppUtil.killProcess(MyApplication.getInstance(), true);
                }
            });
    }

    @Override
    protected void onDestroy() {
        appExit();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Fragment fragment: mFragments) {
            if(fragment != null){
                fragment.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (Fragment fragment: mFragments) {
            if(fragment != null){
                fragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }
}