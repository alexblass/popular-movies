package net.alexblass.popularmovies;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.alexblass.popularmovies.data.FavoritesContract;
import net.alexblass.popularmovies.models.Movie;
import net.alexblass.popularmovies.utilities.MovieAdapter;
import net.alexblass.popularmovies.utilities.MovieLoader;

import static android.R.attr.id;

/**
 * A fragment that shows the grid of movie posters
 * and launches a new activity when the user taps
 * a movie poster.
 */
public class MainActivityFragment extends Fragment
        implements MovieAdapter.ItemClickListener,
        LoaderManager.LoaderCallbacks<Movie[]>,
        SharedPreferences.OnSharedPreferenceChangeListener{

    // Displays a message when there is no Internet or when there are no Movies found
    private TextView mErrorMessageTextView;

    // Loading indicator for a responsive app experience
    private View mLoadingIndicator;

    // A RecyclerView to hold all of our Movie posters and enable smooth scrolling
    private RecyclerView mRecyclerView;

    // Movie adapter to display the Movies correctly
    private MovieAdapter mAdapter;

    // The URL to fetch the Movie JSON data
    private static final String REQUEST_BASE_URL =
            "https://api.themoviedb.org/3/movie/";

    // The user's sort setting preference in a String
    private String sortSetting;

    // A string to hold the complete sort preference URL
    private String requestUrlWithSort;

    // The ID for the MovieLoader
    private static final int MOVIE_LOADER_ID = 0;

    // Empty constructor
    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        setHasOptionsMenu(true);

        // Set up our preferences to determine what sort order to display the movies in
        // Default is by most popular
        setupSharedPreferences();

        // Find the RecyclerView and set our adapter to it so the posters
        // display in a grid format
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.movie_poster_grid);
        mRecyclerView.setLayoutManager(
                new GridLayoutManager(getActivity(), numberOfColumns(getContext())));

        mAdapter = new MovieAdapter(getActivity(), new Movie[0]);
        mAdapter.setClickListener(this);

        mRecyclerView.setAdapter(mAdapter);

        mLoadingIndicator = rootView.findViewById(R.id.loading_indicator);
        mLoadingIndicator.setVisibility(View.VISIBLE);
        mErrorMessageTextView = (TextView) rootView.findViewById(R.id.error_message_tv);

        LoaderManager loaderManager = getLoaderManager();

        ConnectivityManager cm = (ConnectivityManager) getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();

        // If there is no connectivity, show an error message
        if (isConnected) {
            showRecyclerView();
            loaderManager.initLoader(MOVIE_LOADER_ID, null, this);
        } else {
            mLoadingIndicator.setVisibility(View.GONE);
            showErrorMessage();
            mErrorMessageTextView.setText(R.string.no_connection);
        }
        return rootView;
    }

    // Pass the URI to the Movie loader to load the data
    @Override
    public Loader<Movie[]> onCreateLoader(int id, Bundle args) {
        // Build our URL to the movie DB API with our default sort
        requestUrlWithSort = formUrl();

        Uri baseUri = Uri.parse(requestUrlWithSort);
        Uri.Builder uriBuilder = baseUri.buildUpon();

        return new MovieLoader(getContext(), uriBuilder.toString());
    }

    // When the Loader finishes, add the list of Movies to the adapter's data set
    @Override
    public void onLoadFinished(Loader<Movie[]> loader, Movie[] moviesList) {
        mLoadingIndicator.setVisibility(View.GONE);
        mErrorMessageTextView.setText(R.string.no_results);
        mAdapter.setMovies(new Movie[0]);

        if (moviesList != null && moviesList.length > 0){
            mAdapter.setMovies(moviesList);
        }

    }

    // Reset the loader to clear existing data
    @Override
    public void onLoaderReset(Loader<Movie[]> loader) {
        mAdapter.setMovies(new Movie[0]);
    }

    // When the user clicks a poster, launch a new activity with the detail view
    // for the selected Movie
    @Override
    public void onItemClick(View view, int position) {
        Intent intent = new Intent(getActivity(), DetailActivity.class);

        // Pass the current Movie into the new Intent so we can access it's information
        Movie currentMovie = mAdapter.getItem(position);
        intent.putExtra("Movie", currentMovie);

        // Form the URI to the current movie and pass it to the next activity
        Uri currentMovieUri = ContentUris.withAppendedId(FavoritesContract.FavoritesEntry.CONTENT_URI,
                Long.parseLong(currentMovie.getId()));
        intent.setData(currentMovieUri);

        startActivity(intent);
    }

    // Set the data view to visible and the error message view to invisible
    public void showRecyclerView(){
        mErrorMessageTextView.setVisibility(View.INVISIBLE);
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    // Set the data view to invisible and the error message to visible
    public void showErrorMessage(){
        mRecyclerView.setVisibility(View.INVISIBLE);
        mErrorMessageTextView.setVisibility(View.VISIBLE);
    }

    // Create a menu to display the sort settings option
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    // Launch the settings sort order activity when selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // Launch settings preferences activity to allow user to toggle sort order
        if (id == R.id.action_settings) {
            Intent startSettingsActivity = new Intent(getContext(), SettingsActivity.class);
            getActivity().startActivity(startSettingsActivity);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Get the user's preference for the sort order to display
    private void setupSharedPreferences() {
        // Get all of the values from shared preferences to set it up
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Get the default sort setting
        sortSetting = sharedPreferences.getString(getString(R.string.pref_sort_key),
                getString(R.string.pref_sort_popular_value));

        // Register the listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    // Creates the complete URL string for sorting the movies with the user's shared preference settings
    public String formUrl() {
        String stringUrl = REQUEST_BASE_URL + sortSetting +
                "&api_key=" + BuildConfig.THE_MOVIE_DB_API_TOKEN;

        return stringUrl;
    }

    // Calculates the number of columns in the RecyclerView
    public static int numberOfColumns(Context context) {
        int columnWidth = 150;
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
        int numberofColumns = (int) (dpWidth / columnWidth);
        return numberofColumns;
    }

    // Restarts the loader to update the screen if the sort order preference changes
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_sort_key))) {
            sortSetting = sharedPreferences.getString(getString(R.string.pref_sort_key),
                    getString(R.string.pref_sort_popular_value));
            getLoaderManager().restartLoader(MOVIE_LOADER_ID, null, this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister MainActivity as an OnPreferenceChangedListener to avoid any memory leaks.
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}