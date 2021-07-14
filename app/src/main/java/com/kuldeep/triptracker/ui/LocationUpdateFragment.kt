package com.kuldeep.triptracker.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProviders
import androidx.room.Room
import com.kuldeep.triptracker.R
import com.kuldeep.triptracker.data.db.MyLocationDao
import com.kuldeep.triptracker.data.db.MyLocationDatabase
import com.kuldeep.triptracker.data.db.MyLocationEntity
import com.kuldeep.triptracker.databinding.FragmentLocationUpdateBinding
import com.kuldeep.triptracker.hasPermission
import com.kuldeep.triptracker.viewmodels.LocationUpdateViewModel
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.lang.StringBuilder
import java.util.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val TAG = "LocationUpdateFragment"

/**
 * Displays location information via PendingIntent after permissions are approved.
 *
 * Will suggest "enhanced feature" to enable background location requests if not approved.
 */
class LocationUpdateFragment : Fragment() {

    private var activityListener: Callbacks? = null
    private lateinit var binding: FragmentLocationUpdateBinding
    private lateinit var jsonObj: JSONObject

    private val locationUpdateViewModel by lazy {
        ViewModelProviders.of(this).get(LocationUpdateViewModel::class.java)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Callbacks) {
            activityListener = context
            // If fine location permission isn't approved, instructs the parent Activity to replace
            // this fragment with the permission request fragment.
            if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                activityListener?.requestFineLocationPermission()
            }
        } else {
            throw RuntimeException("$context must implement LocationUpdateFragment.Callbacks")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLocationUpdateBinding.inflate(inflater, container, false)
        binding.enableBackgroundLocationButton.setOnClickListener {
            activityListener?.requestBackgroundLocationPermission()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationUpdateViewModel.receivingLocationUpdates.observe(
            viewLifecycleOwner,
            androidx.lifecycle.Observer { receivingLocation ->
                updateStartOrStopButtonState(receivingLocation)
            }
        )

        locationUpdateViewModel.locationListLiveData.observe(
            viewLifecycleOwner,
            androidx.lifecycle.Observer { locations ->
                locations?.let {
                    Log.d(TAG, "Got ${locations.size} locations")
                    if (locations.isEmpty()) {
                        binding.locationOutputTextView.text =
                            getString(R.string.emptyLocationDatabaseMessage)
                    } else {
                        val outputStringBuilder = StringBuilder("")
                        for (location in locations) {
                            outputStringBuilder.append(location.toString() + "\n")
                        }
                        binding.locationOutputTextView.text = outputStringBuilder.toString()

                       //-----------------------for display data export-----------------
                        jsonObj = JSONObject()
                        val locationLast: MyLocationEntity = locations.get(locations.size - 1)
                        val locationFirst: MyLocationEntity = locations.get(0)
                        jsonObj. put("trip_id", "1")
                        jsonObj. put("start_time", locationFirst.date)
                        jsonObj. put("end_time", locationLast.date)
                        val jsonArray = JSONArray()
                        for (location in locations) {
                            val jsonObj1 = JSONObject()
                            val latitude: Double = location.latitude
                            val longitude: Double = location.longitude
                            val date: Date = location.date
                            val accuracy: Double = location.accuracy
                            jsonObj1. put("latitude", latitude)
                            jsonObj1. put("longitude", longitude)
                            jsonObj1. put("timestamps", date)
                            jsonObj1. put("accuracy", accuracy)
                            jsonArray.put(jsonObj1)
                        }
                        jsonObj. put("locations", jsonArray)
                    }
                }
            }
        )

        binding.outputExportButton.apply{
            setOnClickListener {
              //-------------------export data----------------------
               if(binding.startOrStopLocationUpdatesButton.text.equals(getString(R.string.stop_receiving_location))){
                    Toast.makeText(getActivity(),getString(R.string.stop_location_updates), Toast.LENGTH_LONG).show()
                }else{
                    //-------display dialog------------
                   ExportData(jsonObj.toString())
                }
            }
        }
    }
    fun ExportData(str : String) {
        val dialog = AlertDialog.Builder(activity as MainActivity)
        dialog.setTitle("TRIP DATA!")
        dialog.setCancelable(false)
        dialog.setMessage(str)
        dialog.setPositiveButton("Ok"){dialog, which ->
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        updateBackgroundButtonState()
    }

    override fun onPause() {
        super.onPause()

        // Stops location updates if background permissions aren't approved. The FusedLocationClient
        // won't trigger any PendingIntents with location updates anyway if you don't have the
        // background permission approved, but it's best practice to unsubscribing anyway.
        // To simplify the sample, we are unsubscribing from updates here in the Fragment, but you
        // could do it at the Activity level if you want to continue receiving location updates
        // while the user is moving between Fragments.
        if ((locationUpdateViewModel.receivingLocationUpdates.value == true) &&
            (!requireContext().hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION))) {
            locationUpdateViewModel.stopLocationUpdates()
        }
    }

    override fun onDetach() {
        super.onDetach()

        activityListener = null
    }

    private fun showBackgroundButton(): Boolean {
        return !requireContext().hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun updateBackgroundButtonState() {
        if (showBackgroundButton()) {
            binding.enableBackgroundLocationButton.visibility = View.VISIBLE
        } else {
            binding.enableBackgroundLocationButton.visibility = View.GONE
        }
    }

    private fun updateStartOrStopButtonState(receivingLocation: Boolean) {
        if (receivingLocation) {
            binding.startOrStopLocationUpdatesButton.apply {
                text = getString(R.string.stop_receiving_location)
                setOnClickListener {
                    locationUpdateViewModel.stopLocationUpdates()
                }
            }
        } else {
            binding.startOrStopLocationUpdatesButton.apply {
                text = getString(R.string.start_receiving_location)
                setOnClickListener {
                    locationUpdateViewModel.startLocationUpdates()
                }
            }
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments]
     * (http://developer.android.com/training/basics/fragments/communicating.html)
     * for more information.
     */
    interface Callbacks {
        fun requestFineLocationPermission()
        fun requestBackgroundLocationPermission()
    }

    companion object {
        fun newInstance() = LocationUpdateFragment()
    }
}