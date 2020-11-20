package com.android.homework.rxjavahomework

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.FlowableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.InputStream
import java.util.concurrent.TimeUnit

class TextSearchFragment : Fragment() {

    companion object {
        fun newInstance() = TextSearchFragment()
        private const val TAG_DEBUG_FLOWABLE = "FlowableDebug"
    }

    private lateinit var svSearchLine: SearchView
    private lateinit var tvNumberOfCoincidences: TextView
    private lateinit var tvFullText: TextView

    private var disposable: Disposable? = null

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val fragmentLayout = inflater.inflate(R.layout.fragment_text_search, container, false)
        initView(fragmentLayout)
        return fragmentLayout
    }

    private fun initView(fragmentLayout: View) {
        svSearchLine = fragmentLayout.findViewById(R.id.sv_search_line)
        tvNumberOfCoincidences = fragmentLayout.findViewById(R.id.tv_number_of_coincidences)
        tvFullText = fragmentLayout.findViewById(R.id.tv_full_text)
        setTextInTextView()
    }

    private fun setTextInTextView() {
        val inputStream = resources.openRawResource(R.raw.text)
        val stringFromInputStream = getStringFromInputStream(inputStream)
        tvFullText.text = stringFromInputStream
    }

    private fun getStringFromInputStream(inputStream: InputStream) = inputStream.bufferedReader().use { it.readText() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        disposable = createSearchViewFlowable(svSearchLine)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .switchMap {
                    createTextProcessingFlowable(it)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { count ->
                    Log.d(TAG_DEBUG_FLOWABLE, "count in subscribe: $count")
                    tvNumberOfCoincidences.text = count.toString()
                }
    }

    private fun createTextProcessingFlowable(enteredString: String) = Flowable.create(FlowableOnSubscribe<Int> { emitter ->
        if (enteredString.isEmpty()) {
            emitter.onNext(0)
        } else {
            countCoincidencesInText(enteredString, emitter)
        }
    }, BackpressureStrategy.BUFFER)
            .throttleLatest(2, TimeUnit.MILLISECONDS, true)

    private fun countCoincidencesInText(enteredString: String, emitter: FlowableEmitter<Int>) {
        var count = 0
        val listStringsFromFullText = tvFullText.text.toString().split(" ").filter { x -> x != " " }
        listStringsFromFullText.forEach { item ->
            if (enteredString in item) {
                count++
                emitter.onNext(count)
                Log.d(TAG_DEBUG_FLOWABLE, "count in Flowable:  $count")
            }
        }
        if (count == 0) {
            emitter.onNext(0)
        }
    }

    private fun createSearchViewFlowable(searchView: SearchView) = Flowable.create(object : FlowableOnSubscribe<String> {
        override fun subscribe(emitter: FlowableEmitter<String>) {
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { emitter.onNext(it) }
                    return true
                }

                override fun onQueryTextChange(str: String?): Boolean {
                    str?.let { emitter.onNext(it) }
                    return true
                }
            })
        }
    }, BackpressureStrategy.BUFFER)
            .debounce(700, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()

    override fun onDestroy() {
        disposable?.dispose()
        disposable = null
        super.onDestroy()
    }

}