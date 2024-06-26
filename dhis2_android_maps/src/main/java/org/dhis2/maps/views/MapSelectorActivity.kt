package org.dhis2.maps.views

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.engine.LocationEngineDefault
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.location.permissions.PermissionsManager
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillColor
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import org.dhis2.commons.extensions.truncate
import org.dhis2.maps.R
import org.dhis2.maps.camera.initCameraToViewAllElements
import org.dhis2.maps.camera.moveCameraToPosition
import org.dhis2.maps.databinding.ActivityMapSelectorBinding
import org.dhis2.maps.extensions.polygonToLatLngBounds
import org.dhis2.maps.extensions.toLatLng
import org.dhis2.maps.geometry.bound.GetBoundingBox
import org.dhis2.maps.geometry.point.PointViewModel
import org.dhis2.maps.geometry.polygon.PolygonAdapter
import org.dhis2.maps.geometry.polygon.PolygonViewModel
import org.dhis2.maps.layer.basemaps.BaseMapManager
import org.dhis2.maps.location.MapActivityLocationCallback
import org.hisp.dhis.android.core.arch.helpers.GeometryHelper
import org.hisp.dhis.android.core.common.FeatureType
import org.hisp.dhis.android.core.common.Geometry
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle

class MapSelectorActivity :
    AppCompatActivity(),
    MapActivityLocationCallback.OnLocationChanged {

    override fun onLocationChanged(latLng: LatLng) {
        if (!init) {
            init = true
            if (initialCoordinates == null) {
                map.moveCameraToPosition(latLng)
                getPointViewModel()?.let {
                    val point =
                        Point.fromLngLat(
                            latLng.longitude.truncate(),
                            latLng.latitude.truncate(),
                        )
                    setPointToViewModel(point, it)
                }
            }
        }
    }

    private var fieldUid: String? = null
    lateinit var mapView: MapView
    lateinit var map: MapboxMap
    var style: Style? = null
    private lateinit var locationType: FeatureType
    lateinit var binding: ActivityMapSelectorBinding
    private val arrayOfIds = mutableListOf<String>()
    var init: Boolean = false

    private var initialCoordinates: String? = null

    private val baseMapManager by lazy {
        BaseMapManager(this, emptyList())
    }

    private var onSaveButtonClick: (() -> Unit)? = null

    private var pointViewModel: PointViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_map_selector)
        binding.back.setOnClickListener { finish() }
        locationType = intent.getStringExtra(LOCATION_TYPE_EXTRA)?.let { featureName ->
            FeatureType.valueOf(featureName)
        } ?: FeatureType.POINT

        fieldUid = intent.getStringExtra(FIELD_UID)
        initialCoordinates = intent.getStringExtra(INITIAL_GEOMETRY_COORDINATES)
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            mapView.contentDescription = "LOADED"
            map = mapboxMap
            mapboxMap.setStyle(
                baseMapManager.styleJson(
                    baseMapManager.getDefaultBasemap(),
                ),
            ) { style ->
                this.style = style
                enableLocationComponent()
                centerMapOnCurrentLocation()
                when (locationType) {
                    FeatureType.POINT -> bindPoint(initialCoordinates)
                    FeatureType.POLYGON -> bindPolygon(initialCoordinates)
                    else -> finish()
                }
            }
        }
        binding.mapPositionButton.setOnClickListener {
            centerCameraOnMyPosition()
        }

        binding.saveButton.setContent {
            Button(
                style = ButtonStyle.FILLED,
                text = resources.getString(org.hisp.dhis.mobile.ui.designsystem.R.string.done),
                onClick = { onSaveButtonClick?.invoke() },
            )
        }
    }

    private fun centerCameraOnMyPosition() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val isLocationActivated = map.locationComponent.isLocationComponentActivated
            val isLocationEnabled = map.locationComponent.isLocationComponentEnabled
            if (isLocationActivated && isLocationEnabled) {
                map.locationComponent.lastKnownLocation?.let {
                    val latLong = LatLng(it)
                    map.moveCameraToPosition(latLong)
                    getPointViewModel()?.let { viewModel ->
                        val point =
                            Point.fromLngLat(
                                latLong.longitude.truncate(),
                                latLong.latitude.truncate(),
                            )
                        setPointToViewModel(point, viewModel)
                    }
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    CENTER_MY_POSITION,
                )
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                CENTER_MY_POSITION,
            )
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocationComponent() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            val locationComponent = map.locationComponent

            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(
                    this,
                    style!!,
                ).build(),
            )

            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.renderMode = RenderMode.COMPASS
            locationComponent.zoomWhileTracking(13.0)

            LocationEngineDefault.getDefaultLocationEngine(this).getLastLocation(
                MapActivityLocationCallback(this),
            )
        }
    }

    private fun getPointViewModel(): PointViewModel? {
        if (locationType == FeatureType.POINT && pointViewModel == null) {
            pointViewModel = ViewModelProvider(this)[PointViewModel::class.java]
        }
        return pointViewModel
    }

    private fun bindPoint(initialCoordinates: String?) {
        getPointViewModel()?.let { viewModel ->
            binding.recycler.visibility = View.GONE
            map.addOnMapClickListener {
                val point = Point.fromLngLat(it.longitude.truncate(), it.latitude.truncate())
                setPointToViewModel(point, viewModel)
                true
            }
            onSaveButtonClick = {
                val value = viewModel.getPointAsString()
                value?.let {
                    finishResult(it)
                }
            }

            if (initialCoordinates != null) {
                val initGeometry =
                    Geometry.builder().coordinates(initialCoordinates).type(locationType).build()
                val pointGeometry = GeometryHelper.getPoint(initGeometry)
                pointGeometry.let { sdkPoint ->
                    val point = Point.fromLngLat(sdkPoint[0], sdkPoint[1])
                    setPointToViewModel(point, viewModel)
                }
                map.moveCameraToPosition(pointGeometry.toLatLng())
            }
        }
    }

    private fun setPointToViewModel(point: Point, viewModel: PointViewModel) {
        viewModel.setPoint(point)
        viewModel.source?.let { geoSon ->
            viewModel.source = updateSource(point, geoSon)
            return
        }
        viewModel.source = createSource(viewModel.getId(), point)
        viewModel.layer = createLayer(viewModel.getId())
        showSource(
            viewModel.source!!,
            viewModel.layer!!,
            viewModel.getId(),
            com.mapbox.mapboxsdk.R.drawable.maplibre_marker_icon_default,
        )
    }

    private fun updateSource(point: Point, source: GeoJsonSource): GeoJsonSource {
        val geoJson = (style?.getSource(source.id) as GeoJsonSource)
        geoJson.setGeoJson(
            Feature.fromGeometry(
                point,
            ),
        )
        return geoJson
    }

    private fun showSource(source: GeoJsonSource, layer: SymbolLayer, id: String, drawable: Int) {
        style?.addImage(
            id,
            ResourcesCompat.getDrawable(resources, drawable, null)!!.toBitmap(),
        )
        style?.addSource(source)
        style?.addLayer(layer)
    }

    private fun printPoint(
        point: Point,
        source: GeoJsonSource,
        layer: SymbolLayer,
        id: String,
        drawable: Int,
    ) {
        if (style?.getSource(source.id) != null) {
            updateSource(point, source)
        } else {
            showSource(source, layer, id, drawable)
        }
    }

    private fun createLayer(id: String): SymbolLayer {
        val symbolLayer = SymbolLayer(id, id)
        symbolLayer.withProperties(
            PropertyFactory.iconImage(id),
        )
        return symbolLayer
    }

    private fun createSource(id: String, point: Point): GeoJsonSource {
        return GeoJsonSource(
            id,
            Feature.fromGeometry(
                point,
            ),
        )
    }

    private fun bindPolygon(initialCoordinates: String?) {
        val viewModel = ViewModelProvider(this)[PolygonViewModel::class.java]
        viewModel.onMessage = {
            Toast.makeText(this@MapSelectorActivity, it, Toast.LENGTH_SHORT).show()
        }
        binding.recycler.layoutManager = GridLayoutManager(this, 2)
        viewModel.response.observe(
            this,
        ) {
            binding.recycler.adapter = PolygonAdapter(it, viewModel)
            updateVector(it)
        }
        map.addOnMapClickListener {
            val point = Point.fromLngLat(it.longitude, it.latitude)
            val polygonPoint = viewModel.createPolygonPoint()
            polygonPoint.point = point
            polygonPoint.layer = createLayer(polygonPoint.uuid)
            polygonPoint.source = createSource(polygonPoint.uuid, point)
            viewModel.add(polygonPoint)
            true
        }
        onSaveButtonClick = {
            val value = viewModel.getPointAsString()
            value?.let {
                finishResult(it)
            }
        }
        if (initialCoordinates != null) {
            val initGeometry =
                Geometry.builder().coordinates(initialCoordinates).type(locationType).build()
            val polygons = GeometryHelper.getPolygon(initGeometry)
            polygons.forEach {
                it.forEach { sdkPoint ->
                    val point = Point.fromLngLat(sdkPoint[0], sdkPoint[1])
                    val polygonPoint = viewModel.createPolygonPoint()
                    polygonPoint.point = point
                    polygonPoint.layer = createLayer(polygonPoint.uuid)
                    polygonPoint.source = createSource(polygonPoint.uuid, point)
                    viewModel.add(polygonPoint)
                }
            }
            polygons.polygonToLatLngBounds(GetBoundingBox())?.let { bounds ->
                map.initCameraToViewAllElements(this, bounds)
            }
        }
    }

    private fun updateVector(list: MutableList<PolygonViewModel.PolygonPoint>) {
        style?.let { style ->
            val sourceName = "polygon_source"
            style.removeLayer(sourceName)
            style.removeSource(sourceName)
            arrayOfIds.forEach { id ->
                style.getLayer(id)?.let { layer ->
                    style.removeLayer(layer)
                }
                style.getSource(id)?.let {
                    style.removeSource(it)
                }
            }
            arrayOfIds.clear()
            val points = mutableListOf<MutableList<Point>>()
            points.add(mutableListOf())
            list.forEach { point ->
                point.point?.let {
                    points[0].add(it)
                    arrayOfIds.add(point.uuid)
                    printPoint(
                        it,
                        point.source!!,
                        point.layer!!,
                        point.uuid,
                        R.drawable.ic_oval_green,
                    )
                }
            }
            if (points[0].size > 2) {
                if (style.getSource(sourceName) == null) {
                    style.addSource(GeoJsonSource(sourceName, Polygon.fromLngLats(points)))
                    style.addLayerBelow(
                        FillLayer(sourceName, sourceName).withProperties(
                            fillColor(ContextCompat.getColor(this, org.dhis2.commons.R.color.green_7ed)),
                        ),
                        "settlement-label",
                    )
                } else {
                    (style.getSource(sourceName) as GeoJsonSource).setGeoJson(
                        Polygon.fromLngLats(
                            points,
                        ),
                    )
                }
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    public override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private fun centerMapOnCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                ACCESS_LOCATION_PERMISSION_REQUEST,
            )
            return
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            ACCESS_LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    enableLocationComponent()
                    centerMapOnCurrentLocation()
                }
            }

            CENTER_MY_POSITION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    enableLocationComponent()
                    centerCameraOnMyPosition()
                }
            }
        }
    }

    companion object {
        private const val ACCESS_LOCATION_PERMISSION_REQUEST = 102
        private const val CENTER_MY_POSITION = 103
        const val DATA_EXTRA = "data_extra"
        const val LOCATION_TYPE_EXTRA = "LOCATION_TYPE_EXTRA"
        const val INITIAL_GEOMETRY_COORDINATES = "INITIAL_DATA"
        const val FIELD_UID = "FIELD_UID_EXTRA"

        fun create(activity: Context, locationType: FeatureType): Intent {
            val intent = Intent(activity, MapSelectorActivity::class.java)
            intent.putExtra(LOCATION_TYPE_EXTRA, locationType.toString())
            return intent
        }

        fun create(activity: Context, locationType: FeatureType, initialData: String?): Intent {
            val intent = Intent(activity, MapSelectorActivity::class.java)
            intent.putExtra(LOCATION_TYPE_EXTRA, locationType.toString())
            if (initialData != null) {
                intent.putExtra(INITIAL_GEOMETRY_COORDINATES, initialData)
            }
            return intent
        }

        fun create(
            activity: Context,
            fieldUid: String,
            locationType: FeatureType,
            initialData: String?,
        ): Intent {
            val intent = Intent(activity, MapSelectorActivity::class.java)
            intent.putExtra(FIELD_UID, fieldUid)
            intent.putExtra(LOCATION_TYPE_EXTRA, locationType.toString())
            if (initialData != null) {
                intent.putExtra(INITIAL_GEOMETRY_COORDINATES, initialData)
            }
            return intent
        }
    }

    private fun finishResult(value: String) {
        val intent = Intent()
        intent.putExtra(FIELD_UID, fieldUid)
        intent.putExtra(DATA_EXTRA, value)
        intent.putExtra(LOCATION_TYPE_EXTRA, locationType.toString())
        setResult(RESULT_OK, intent)
        finish()
    }
}
