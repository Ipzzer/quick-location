package com.codebase.quicklocation;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.codebase.quicklocation.database.Favorites;
import com.codebase.quicklocation.database.dao.FavoritesDao;
import com.codebase.quicklocation.model.LastLocation;
import com.codebase.quicklocation.model.Location;
import com.codebase.quicklocation.model.PlaceDetail;
import com.codebase.quicklocation.model.ResponseForPlaceDetails;
import com.codebase.quicklocation.utils.HTTPTasks;
import com.codebase.quicklocation.utils.Reporter;
import com.codebase.quicklocation.utils.Utils;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Scanner;

public class PlaceDetailActivity extends AppCompatActivity {
    private ImageView ivPlacePhoto;
    private TextView tvPlaceDirection;
    private TextView tvWebsite;
    private TextView tvPlacePhone;
    private TextView tvPlaceOpeningHours;
    private TextView tvOpeningStatus;
    private Button callButton;
    private String strPlaceName;
    private String strPlaceDirection;
    private String strPlacePhone;
    private String strPlaceId;
    private String strCategory;
    private Double doubleRating;
    private StringBuilder strOpeningHours;
    private ResponseForPlaceDetails response;
    private Toolbar toolbar;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private Reporter logger = Reporter.getInstance(PlaceDetailActivity.class);
    private FavoritesDao dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_detail);
        dao = new FavoritesDao(this);
        collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing);
        collapsingToolbarLayout.setTitleEnabled(false);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        ivPlacePhoto = (ImageView) findViewById(R.id.iv_place_photo);
        tvPlacePhone = (TextView) findViewById(R.id.tv_phone_number);
        tvPlaceDirection = (TextView) findViewById(R.id.tv_place_direction);
        tvPlaceOpeningHours = (TextView) findViewById(R.id.tv_opening_hours);
        tvWebsite = (TextView) findViewById(R.id.tv_website);
        tvOpeningStatus = (TextView) findViewById(R.id.tv_opening_status);
        callButton = (Button) findViewById(R.id.call_action_button);

        try {
            Bundle bundle = getIntent().getExtras();

            strPlaceName = bundle.getString(PlaceActivity.KEY_PLACE_NAME);
            setTitle(strPlaceName);

            strPlaceId = bundle.getString(PlaceActivity.KEY_PLACE_ID);
            strCategory = bundle.getString(PlaceActivity.KEY_APP_CATEGORY);
            doubleRating = bundle.getDouble(PlaceActivity.KEY_PLACE_RATING);

            Button button = (Button) findViewById(R.id.button_add_favorite);
            ImageView imageView = (ImageView) findViewById(R.id.image_add_favorite);
            Favorites f = dao.getByPlaceId(strPlaceId);

            if(f != null) {
                button.setVisibility(View.GONE);
                imageView.setVisibility(View.GONE);
            }

            String key = Utils.giveMeMyCandy();

            if (key != null) {
                String url = getString(R.string.google_api_place_details_url) + "placeid=" + strPlaceId + "&key=" + key;
                DownloadDetailOfPlace downloader = new DownloadDetailOfPlace();
                downloader.execute(url);
            } else
                Snackbar.make(toolbar, "No se encontró el key de acceso al API", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            logger.error(Reporter.stringStackTrace(e));
        }
        File directImge = new File(Utils.targetPath);
        if (!directImge.exists()) {
            directImge.mkdirs();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Este metodo realiza la llamada al local
     * al presionar el boton Llamar
     *
     * @param v vista padre del boton (no se utiliza pero es necesaria para el
     *          funcionamiento del click handler)
     */
    public void makeACall(View v) {
        try {
            Intent i = new Intent(Intent.ACTION_CALL);
            i.setData(Uri.parse("tel:" + strPlacePhone));
            startActivity(i);
        } catch (SecurityException se) {
            logger.error(Reporter.stringStackTrace(se));
        }
    }

    /**
     * Este metodo realiza lo siguiente:
     * Obtiene la coordenada actual del usuario
     * Obtiene la coordenada del lugar escogido
     *
     * @param v vista padre del boton (no se utiliza pero es necesaria para el
     *          funcionamiento del click handler)
     */
    public void goToThePlace(View v) {
        try {
            String lastLocation = Utils.getSavedLocation(PlaceDetailActivity.this);

            if (!"no_location".equals(lastLocation)) {
                LastLocation userLocation = Utils.factoryGson().fromJson(lastLocation, LastLocation.class);
                Date date = new Date(userLocation.getTime());
                Log.e("GPSTrackingService", date.toString());

                if (userLocation != null) {
                    Location placeLocation = response.getResult().getGeometry().getLocation();
                    String queryParams = placeLocation.getLat() + "," + placeLocation.getLng();
                    Uri gmmIntentUri = Uri.parse("google.navigation:q=" + queryParams);
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    startActivity(mapIntent);
                }
            } else {
                //TODO proveer alternativa para cuando no se encuentra archivo de coordenada en disco
                Snackbar.make(findViewById(android.R.id.content), "Had a snack at Snackbar", Snackbar.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            logger.error(Reporter.stringStackTrace(e));
        }
    }

    private class DownloadDetailOfPlace extends AsyncTask<String, String, String> {
        private String apiResponse;
        ProgressDialog progressDialog;

        @Override
        protected String doInBackground(String... params) {
            logger.write("Calling place details API ...");
            try {
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
                response = Utils.factoryGson().fromJson(result, ResponseForPlaceDetails.class);

                if ("OK".equals(response.getStatus())) {
                    PlaceDetail detail = response.getResult();

                    //Búsqueda de la foto del lugar en el API
                    //if (detail.getPhotos() != null && detail.getPhotos().length > 0) {
                    if (detail.getPhotos() != null && detail.getPhotos().size() > 0) {
                        String key = Utils.giveMeMyCandy();

                        if (key != null) {
                            String photoUrl = getString(R.string.google_api_place_photo_url) +
                                    "maxwidth=12040&photoreference=" + detail.getPhotos().get(0).getPhotoReference() +
                                    "&key=" + Utils.giveMeMyCandy();

                            //logger.write("Photo place URL: " + photoUrl);

                            DisplayMetrics metrics = new DisplayMetrics();
                            getWindowManager().getDefaultDisplay().getMetrics(metrics);

                            int height = (int) (metrics.heightPixels * 0.50);

                            Picasso.with(PlaceDetailActivity.this)
                                    .load(photoUrl)
                                    .error(R.drawable.default_img)
                                    .resize(metrics.widthPixels, height)
                                    .into(ivPlacePhoto);
                        } else {
                            //logger.write("No se pudo ubicar el key de acceso al API al momento de buscar el logo del local");
                            verificarBitmapLocal();

                        }
                    } else {
                        verificarBitmapLocal();
                    }

                    if (detail.getFormattedPhoneNumber() != null) {
                        strPlacePhone = detail.getFormattedPhoneNumber();
                        if ("".equals(strPlacePhone)) {
                            strPlacePhone = "Dato no disponible";
                            callButton.setEnabled(false);
                        }
                    } else {
                        callButton.setEnabled(false);
                        strPlacePhone = "Dato no disponible";
                    }

                    if (detail.getFormattedAddress() != null)
                        strPlaceDirection = detail.getFormattedAddress();

                    if (detail.getOpeningHours() != null) {
                        //if (detail.getOpeningHours().getWeekdayText() != null && detail.getOpeningHours().getWeekdayText().length > 0) {
                        if (detail.getOpeningHours().getWeekdayText() != null && detail.getOpeningHours().getWeekdayText().size() > 0) {
                            strOpeningHours = new StringBuilder();

                            for (String str : detail.getOpeningHours().getWeekdayText())
                                strOpeningHours.append(str).append("\n");
                        }

                        if (strOpeningHours.equals(""))
                            tvPlaceOpeningHours.setText("Dato no disponible");
                        else
                            tvPlaceOpeningHours.setText(Utils.formatDays(strOpeningHours));

                        if (detail.getOpeningHours().isOpenNow()) {
                            tvOpeningStatus.setText("Abierto en este momento");
                            tvOpeningStatus.setTextColor(ContextCompat.getColor(PlaceDetailActivity.this, R.color.primary_dark));
                        } else {
                            tvOpeningStatus.setText("Cerrado en este momento");
                            tvOpeningStatus.setTextColor(ContextCompat.getColor(PlaceDetailActivity.this, android.R.color.holo_red_dark));
                        }
                    } else
                        tvPlaceOpeningHours.setText("Dato no disponible");

                    tvPlacePhone.setText(strPlacePhone);
                    tvPlaceDirection.setText(strPlaceDirection);

                    if (detail.getWebsite() == null)
                        tvWebsite.setText("Dato no disponible");
                    else
                        tvWebsite.setText(detail.getWebsite());


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
            progressDialog = ProgressDialog.show(PlaceDetailActivity.this, "Buscando", "Por favor espere ...");
        }

        @Override
        protected void onProgressUpdate(String... text) {
        }
    }

    public void goToWebsite(View view){
        if (!tvWebsite.getText().equals("Dato no disponible")){
            String url = tvWebsite.getText().toString();
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        }
    }

    private void verificarBitmapLocal() {
        Bitmap opcImagePlace = null;
        try {
            opcImagePlace = MediaStore.Images.Media.getBitmap(PlaceDetailActivity.this.getContentResolver(), Utils.getImageUri(strPlaceId));
        } catch (IOException e) {
            logger.error(Reporter.stringStackTrace(e));
        }
        if (opcImagePlace != null) {
            //ivPlacePhoto.setImageBitmap(opcImagePlace);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            int height = (int) (metrics.heightPixels * 0.158);

            Picasso.with(PlaceDetailActivity.this)
                    .load(Utils.getImageUri(strPlaceId))
                    .error(R.drawable.default_img)
                    .resize(metrics.widthPixels, height)
                    .into(ivPlacePhoto);

        }else {
            ivPlacePhoto.setImageResource(R.drawable.default_img);
        }
    }

    /**
     * Guarda la seleccion en la tabla de favoritos
     *
     * @param v parametro de vista
     */
    public void guardarFavorito(View v) {
        dao = new FavoritesDao(this);
        Favorites f = dao.getByPlaceId(strPlaceId);
        if (f == null) {
            Favorites favorite = new Favorites();
            favorite.setLocalName(strPlaceName);
            favorite.setRating(doubleRating);
            favorite.setCategory(strCategory);
            favorite.setAddedFrom(new Date());
            favorite.setPlaceId(strPlaceId);
            Intent i = new Intent(PlaceDetailActivity.this, AddFavoritesActivity.class);
            String cdata = Utils.objectToJson(favorite);
            String detailsResponse = Utils.objectToJson(response);
            i.putExtra("placeDetails", detailsResponse);
            i.putExtra("cdata", cdata);
            startActivity(i);
        } else {
            Snackbar.make(toolbar, "Favorito ya existe", Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        //verificarBitmapLocal();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.place_detail_menu, menu);
        return true;
    }

    /**
     * Este metodo despliega la ventana de Mejora de Informacion
     * @param item menu item que debe ser clickeado
     */
    public void showImprovementScreen(MenuItem item) {
        Intent i = new Intent(PlaceDetailActivity.this, ImprovementActivity.class);
        i.putExtra("place_id", strPlaceId);
        i.putExtra("api_response", Utils.objectToJson(response));
        startActivity(i);
    }

    /**
     * Este metodo responde ante el evento click sobre el item de menu
     * Quejas o Sugerencias, despliega la ventana de Reportes
     * @param item menu item que debe ser clickeado
     */
    public void showCommentsScreen(MenuItem item) {
        Intent i = new Intent(PlaceDetailActivity.this, ReportActivity.class);
        i.putExtra("place_id", strPlaceId);
        startActivity(i);
    }
}
