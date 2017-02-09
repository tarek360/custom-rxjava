package com.tarek360.sample_app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import custom.reactivex.Observable;
import custom.reactivex.ObservableEmitter;
import custom.reactivex.ObservableOnSubscribe;
import custom.reactivex.Observer;
import custom.reactivex.disposables.Disposable;
import custom.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getObservable()

                // Run on a background thread
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<List<ApiUser>>() {

                    @Override
                    public void onSubscribe(Disposable d) {
                        Log.d(TAG, " onSubscribe : " + d.isDisposed());
                    }

                    @Override
                    public void onNext(List<ApiUser> userList) {
                        for (ApiUser user : userList) {
                            Log.d(TAG, " user : " + user.name);
                        }
                        Log.d(TAG, " onNext : " + userList.size());
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, " onError : " + e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, " onComplete");
                    }
                });

    }


    private Observable<List<ApiUser>> getObservable() {
        return Observable.create(new ObservableOnSubscribe<List<ApiUser>>() {
            @Override
            public void subscribe(ObservableEmitter<List<ApiUser>> e) throws Exception {
                if (!e.isDisposed()) {

                    ArrayList<ApiUser> list = new ArrayList<>();
                    list.add(new ApiUser());
                    e.onNext(list);
                    e.onComplete();
                }
            }
        });
    }


    public class ApiUser {
        public String name = "ahmed";
    }
}
