package com.codebase.quicklocation;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.codebase.quicklocation.adapters.PlaceItemAdapter;
import com.codebase.quicklocation.model.LastLocation;
import com.codebase.quicklocation.model.Place;
import com.codebase.quicklocation.model.ResponseForPlaces;
import com.codebase.quicklocation.utils.HTTPTasks;
import com.codebase.quicklocation.utils.Reporter;
import com.codebase.quicklocation.utils.Utils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

public class PlaceActivity extends AppCompatActivity {
    static final String KEY_CATEGORY = "categoria";
    static final String KEY_APP_CATEGORY = "app_categoria";
    static final String KEY_PLACE_ID = "placeId";
    static final String KEY_PLACE_NAME = "placeName";
    private String key;
    private String categoria;
    private String appCategoria;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private Toolbar toolbar;
    private Reporter logger = Reporter.getInstance(PlaceActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place);
        Bundle bundle = getIntent().getExtras();
        //data contiene la categoria seleccionada por el usuario en la pantalla anterior
        categoria = bundle.getString(KEY_CATEGORY);
        appCategoria = bundle.getString(KEY_APP_CATEGORY);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try {
            //descargar data
            String lastLocation = Utils.getSavedLocation(PlaceActivity.this);
            if (!"no_location".equals(lastLocation)) {
                LastLocation userLocation = Utils.factoryGson().fromJson(lastLocation, LastLocation.class);
                //Date date = new Date(userLocation.getTime()); //fecha de la ultima coordenada guardada
                key = Utils.getApplicationKey(this);
                if(key != null) {
                    String url = getString(R.string.google_api_nearby_search_url) + "location=" + userLocation.getLatitude() + "," + userLocation.getLongitude() + "&rankby=distance" + "&type=" + categoria + "&key=" + key;
                    DownloadListOfPlaces downloader = new DownloadListOfPlaces();
                    downloader.execute(url);
                } else
                    Snackbar.make(toolbar, "No se encontró el key de acceso al API", Snackbar.LENGTH_LONG).show();
            } else {
                Snackbar.make(toolbar, "No fue posible determinar tu ubicacion actual", Snackbar.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            logger.error(Reporter.stringStackTrace(e));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private class DownloadListOfPlaces extends AsyncTask<String, String, String> {
        private String apiResponse;
        ProgressDialog progressDialog;

        @Override
        protected String doInBackground(String... params) {
            try {
                logger.write("Calling places API ...");
                InputStream streamResponse = HTTPTasks.getJsonFromServer(params[0]);
                return new Scanner(streamResponse).useDelimiter("\\A").next();
            } catch (Exception e) {
                apiResponse = "Error! : " + e.getMessage();
                logger.error(Reporter.stringStackTrace(e));
            }
            return apiResponse;
        }


        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();
            if (!result.contains("Error!")) {
                ResponseForPlaces response = Utils.factoryGson().fromJson(result, ResponseForPlaces.class);
                if ("OK".equals(response.getStatus())) {
                    List<Place> places = response.getResults();
                    //Ajustar la data para mostrar en la lista de resultados
                    recyclerView = (RecyclerView) findViewById(R.id.list_of_places);
                    DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
                    recyclerView.addItemDecoration(dividerItemDecoration);
                    recyclerView.setLayoutManager(new LinearLayoutManager(PlaceActivity.this));
                    recyclerView.setHasFixedSize(true);
                    recyclerView.addItemDecoration(dividerItemDecoration);

                    mAdapter = new PlaceItemAdapter(places, appCategoria, new PlaceItemAdapter.OnItemClickListener() {
                        @Override
                        public void onItemClick(Place item) {
                            Intent i = new Intent(PlaceActivity.this, PlaceDetailActivity.class);
                            i.putExtra(KEY_PLACE_ID, item.getPlaceId());
                            i.putExtra(KEY_PLACE_NAME, item.getName());
                            startActivity(i);
                        }
                    });

                    recyclerView.setAdapter(mAdapter);
                } else if ("ZERO_RESULTS".equals(response.getStatus())) {
                    //TODO: proveer la informacion necesaria, de ser posible realizar en este punto una busqueda mas amplia
                    Snackbar.make(toolbar, "Tu busqueda no arrojo resultados", Snackbar.LENGTH_SHORT).show();
                } else {
                    //TODO: Caso probado colocar pantalla de informacion
                    Snackbar.make(toolbar, response.getStatus(), Snackbar.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(PlaceActivity.this, "Buscando", "Por favor espere ...");
        }


        @Override
        protected void onProgressUpdate(String... text) {
        }
    }
}
