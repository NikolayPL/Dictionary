package nickerman.com.dictionary2.screens.main;

import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import nickerman.com.dictionary2.base.Constants;
import nickerman.com.dictionary2.navigation.Navigator;
import nickerman.com.dictionary2.navigation.Screen;
import nickerman.com.dictionary2.navigation.ScreenType;
import nickerman.com.dictionary2.room.TranslateWordRoomDatabase;
import nickerman.com.dictionary2.room.WordDataSource;
import nickerman.com.dictionary2.room.entity.TranslateWord;
import nickerman.com.dictionary2.room.repository.Repository;


public class MainPresenter implements MainContract.Presenter {

    private MainContract.View view;
    private CompositeDisposable subscriptions;
    private Navigator navigator;
    private ClickCallback clickCallback;
    private List<TranslateWord> listAllWords = new ArrayList<>();

    //new
    private Repository mRepository;
    private List<TranslateWord> listTranslateWords = new ArrayList<>();

    public MainPresenter(TranslateWordRoomDatabase translateWordRoomDatabase) {

        this.mRepository = Repository.getInstance(WordDataSource.getInstance(translateWordRoomDatabase.translateWordDAO()));

        this.clickCallback = new ClickCallback() {
            @Override
            public void editItem(int position) {
                Bundle args = new Bundle();
                args.putString(Constants.POSITION, position + "");
                navigator.navigateTo(Screen.EDIT, ScreenType.ACTIVITY, args);

            }

            @Override
            public void deleteItem(int position, int idDeletedWord) {
                getWordByPositionAndDeleteWord(idDeletedWord);
                view.setEmptyTextSearchField();

            }
        };
    }

    private void deleteWord(TranslateWord deletedWord) {
        Disposable disposable = Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                mRepository.deleteWord(deletedWord);
                emitter.onComplete();
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) throws Exception {
                        Log.d("error", "ok");
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.d("error", throwable.getLocalizedMessage());
                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {
                        loadData();
                    }
                });
        subscriptions.add(disposable);

    }

    private void getWordByPositionAndDeleteWord(int idInDB) {
        Disposable disposable = mRepository.getTranslateWordById(idInDB)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<TranslateWord>() {
                    @Override
                    public void accept(TranslateWord translateWord) throws Exception {
                        deleteWord(translateWord);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.d("data load", "load data field");
                    }
                });

        subscriptions.add(disposable);
    }

    @Override
    public void start(MainContract.View view) {
        this.view = view;
        subscriptions = new CompositeDisposable();
        loadData();
        initAction();


    }

    private void loadData() {
        //get all data into DB
        Disposable disposable = mRepository.getAllWords()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<List<TranslateWord>>() {
                    @Override
                    public void accept(List<TranslateWord> translateWords) throws Exception {
                        onGetAllWordSuccess(translateWords);
                        getAllWords(translateWords);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {

                    }
                });

        subscriptions.add(disposable);
    }

    private void getAllWords(List<TranslateWord> translateWords) {
        listAllWords.clear();
        listAllWords.addAll(translateWords);
    }

    //update list with data and create new with notify adapter
    private void onGetAllWordSuccess(List<TranslateWord> translateWords) {
        listTranslateWords.clear();
        listTranslateWords.addAll(translateWords);
        view.notifyAdapter();
    }

    private void initAction() {
        view.setWordAdapter(listTranslateWords, clickCallback);

        view.search()
                .skip(1)
                .subscribe(new Observer<CharSequence>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        subscriptions.add(d);
                    }

                    @Override
                    public void onNext(CharSequence charSequence) {
                        view.showProgressBar(true);

                        String searchWord = charSequence.toString().trim().toLowerCase();
                        List<TranslateWord> searchingListWord = new ArrayList<>();

                        for (TranslateWord item : listAllWords) {
                            String englishWord = item.getEnglishWord().toLowerCase();
                            String translateWord = item.getTranslateWord().toLowerCase();
                            int counter = 0;

                            if (searchWord.length() <= englishWord.length()) {
                                if (searchWord.equals(englishWord.substring(0, searchWord.length()))) {
                                    searchingListWord.add(item);
                                    counter++;
                                }
                            }

                            if (counter == 0) {
                                if (searchWord.length() <= translateWord.length()) {
                                    if (searchWord.equals(translateWord.substring(0, searchWord.length()))) {
                                        searchingListWord.add(item);
                                    }

                                }
                            }
                        }

                        //setListWords
                        if (searchingListWord.size() > 0) {
                            onGetAllWordSuccess(searchingListWord);
                        } else {
                            loadData();
                        }
                        view.showProgressBar(false);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });

        view.addWord().subscribe(new Observer<Object>() {
            @Override
            public void onSubscribe(Disposable d) {
                subscriptions.add(d);
            }

            @Override
            public void onNext(Object o) {
                //navigate to add activity
                navigator.navigateTo(Screen.ADD_WORD, ScreenType.ACTIVITY);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Override
    public void stop() {
        subscriptions.dispose();
        subscriptions = null;
    }


    @Override
    public void setNavigation(Navigator navigation) {
        this.navigator = navigation;
    }
}
