package com.dbest.covidtracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.robinhood.ticker.TickerUtils
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val BASE_URL = "https://covidtracking.com/api/v1/"
private const val TAG = "MainActivity"
private const val ALL_STATES = "All (Nationwide)"
class MainActivity : AppCompatActivity() {
    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = getString(R.string.app_description)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService = retrofit.create(CovidService::class.java)

        //Fetch the national data
        covidService.getNationalData().enqueue(object: Callback<List<CovidData>>{
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG,"onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "onResponse $response")
                val nationalData = response.body()
                if (nationalData == null){
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }
                setUpEventListener()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG,"Update graph with national data")
                // Update graph with national data
                updateDisplayWithData(nationalDailyData)
            }

        })

        //Fetch the state data
        covidService.getStateData().enqueue(object: Callback<List<CovidData>>{
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG,"onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "onResponse $response")
                val stateData = response.body()
                if (stateData == null){
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }
                perStateDailyData = stateData.reversed().groupBy { it.state }
                Log.i(TAG,"Update spinner with state names")
                // Update spinner with state names
                updateSpinnerWithStateData(perStateDailyData.keys)
            }
        })

    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0, ALL_STATES)

        // Add state list as the data source for the spinner
        spinnerSelect.attachDataSource(stateAbbreviationList)
        spinnerSelect.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as  String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }
    }

    private fun setUpEventListener() {
        tickerView.setCharacterLists(TickerUtils.provideNumberList())

        // Add a listener for users scrubbing on the chart
        sparkView.isScrubEnabled = true
        sparkView.setScrubListener { itemData ->
            if (itemData is CovidData){
                updateInfoForDate(itemData)
            }
        }
        //Respond to radio button selected event
        radioGroupTimeSelection.setOnCheckedChangeListener(){_, checkedId ->
            adapter.daysAgo = when(checkedId){
                R.id.radioButtonWeek -> TimeScale.WEEK
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        radioGroupMetricSelection.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId){
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonDeath -> updateDisplayMetric(Metric.DEATH)

            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        //Update the colour of the chart
        val colorResource = when(metric){
            Metric.NEGATIVE -> R.color.colorNegative
            Metric.POSITIVE -> R.color.colorPositive
            Metric.DEATH -> R.color.colorDeath
        }
        @ColorInt val colorInt = ContextCompat.getColor(this, colorResource)
        sparkView.lineColor = colorInt

        //Update the metric on the adapter
        adapter.metric = metric
        adapter.notifyDataSetChanged()

        //Reset number and date shown in the bottom text views
        updateInfoForDate(currentlyShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
        //Create a new spark Adapter with the data
        adapter = CovidSparkAdapter(dailyData)
        sparkView.adapter =  adapter
        //Update the radio button to select  positive cases and max time by default
        radioButtonPositive.isChecked = true
        radioButtonMax.isChecked = true
        //Display metric for the most recent date
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when(adapter.metric){
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
        tickerView.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)
    }
}
