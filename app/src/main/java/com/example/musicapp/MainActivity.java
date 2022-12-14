package com.example.musicapp;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chibde.visualizer.BarVisualizer;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.jgabrielfreitas.core.BlurImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import jp.wasabeef.recyclerview.adapters.ScaleInAnimationAdapter;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    SongAdapter songAdapter;
    List<Song> allSongs = new ArrayList<>();
    ActivityResultLauncher<String> storagePermissionLauncher;
    final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;

    ExoPlayer player;
    ActivityResultLauncher<String> recordAudioPermissionLauncher; //to be accessed in the song adapter
    final String recordAudioPermission = Manifest.permission.RECORD_AUDIO;
    ConstraintLayout playerView;
    TextView playerCloseBtn;

    //controls
    TextView songNameView, skipPreviousBtn, skipNextBtn, playPauseBtn, repeatModeBtn, playListBtn;
    TextView homeSongNameView, homeSkipPreviousBtn, homePlayPauseBtn, homeSkipNextBtn;

    //wrapper
    ConstraintLayout homeControlWrapper, headWrapper, artWorkWrapper, seekbarWrapper, controlWrapper,
            audioVisualizerWrapper;

    //artwork
    CircleImageView artworkView;

    //seekbar
    SeekBar seekbar;
    TextView progessView, duratioView;

    //audiovisulizer
    BarVisualizer audioVisualizer;

    //blur image view
    BlurImageView blurImageView;

    //status bar & navigation color
    int defaultStatusColor;

    //repeatmode
    int repeatMode = 1; //repeat all = 1, repeat one = 2 , shuffle all = 3

    SearchView searchView;

    boolean isBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //save status color
        defaultStatusColor = getWindow().getStatusBarColor();
        //set the navigation
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199)); //0 & 255

        //toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(getResources().getString(R.string.app_name));

        //recyclerview
        recyclerView = findViewById(R.id.recyclerview);
        storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), grandted -> {
            if (grandted) {
                //fetch songs
                fetchSongs();
            } else {
                userResponnes();
            }
        });

        // launch storage permission on create
        // storagePermissionLauncher.launch(permission);

        //record audio permission
        recordAudioPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && player.isPlaying()) {
                activateAudioVisualizer();
            } else {
                userResponnesOnRecordAudioPerm();
            }
        });
        //views
//        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.playerView);
        playerCloseBtn = findViewById(R.id.playerCloseBtn);
        songNameView = findViewById(R.id.songNameView);
        skipPreviousBtn = findViewById(R.id.skipPreviousBtn);
        skipNextBtn = findViewById(R.id.skipNextBtn);
        playerView = findViewById(R.id.playerView);
        repeatModeBtn = findViewById(R.id.repeatModeBtn);
        playListBtn = findViewById(R.id.playlistBtn);
        playPauseBtn = findViewById(R.id.playPauseBtn);

        homeSongNameView = findViewById(R.id.homeSongNameView);
        homeSkipPreviousBtn = findViewById(R.id.homeSkipPreviousBtn);
        homeSkipNextBtn = findViewById(R.id.homeSkipNextBtn);
        homePlayPauseBtn = findViewById(R.id.homePlayPauseBtn);

        //wrapper
        homeControlWrapper = findViewById(R.id.homeControlWrapper);
        headWrapper = findViewById(R.id.headWrapper);
        artWorkWrapper = findViewById(R.id.artworkWrapper);
        seekbarWrapper = findViewById(R.id.seekbarWrapper);
        controlWrapper = findViewById(R.id.controlWrapper);
        audioVisualizerWrapper = findViewById(R.id.audioVisualizerWrapper);

        //artwork
        artworkView = findViewById(R.id.artworkView);
        seekbar = findViewById(R.id.seekbar);
        progessView = findViewById(R.id.progressView);
        duratioView = findViewById(R.id.durationView);
        audioVisualizer = findViewById(R.id.visualizer);
        blurImageView = findViewById(R.id.blurImageView);

        //player controls method
        //playControls();

        //bind to the player service, and do every thing after binding
        doBindService();
    }

    private void doBindService() {
        Intent playerServiceIntent = new Intent(this, PlayerService.class);
        bindService(playerServiceIntent, playerServiceConnection, Context.BIND_AUTO_CREATE);
    }

    ServiceConnection playerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            PlayerService.ServiceBinder binder = (PlayerService.ServiceBinder) iBinder;
            player = binder.getPlayerService().player;
            isBound = true;

            storagePermissionLauncher.launch(permission);
            playControls();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @Override
    public void onBackPressed() {
        if (playerView.getVisibility() == View.VISIBLE)
            exitPlayerView();
        else
            super.onBackPressed();

    }

    private void playControls() {
        // song names marquee
        songNameView.setSelected(true);
        homeSongNameView.setSelected(true);

        //exit the player view
        playerCloseBtn.setOnClickListener(view -> exitPlayerView());
        playListBtn.setOnClickListener(view -> exitPlayerView());
        //open player view on home control wrapper click
        homeControlWrapper.setOnClickListener(view -> showPlayerView());

        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                Player.Listener.super.onMediaItemTransition(mediaItem, reason);
                //show the title
                assert mediaItem != null;
                songNameView.setText(mediaItem.mediaMetadata.title);
                homeSongNameView.setText(mediaItem.mediaMetadata.title);

                progessView.setText(getReadableTime((int) player.getCurrentPosition()));
                seekbar.setProgress((int) player.getCurrentPosition());
                seekbar.setMax((int) player.getDuration());
                duratioView.setText(getReadableTime((int) player.getDuration()));
                playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_outline, 0, 0, 0);
                homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);

                //show the current artwork
                showCurrentArtWork();
                //update the progress of a current playing song
                updatePlayerPositionProgress();
                //load the artwork animation
                artworkView.setAnimation(loadRotation());

                //set audio visualizer
                activateAudioVisualizer();
                //update play view colors
                updatePlayerColors();

                if (!player.isPlaying()) {
                    player.play();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Player.Listener.super.onPlaybackStateChanged(playbackState);
                if (playbackState == ExoPlayer.STATE_READY) {
                    //set values to player views
                    songNameView.setText(Objects.requireNonNull(player.getCurrentMediaItem()).mediaMetadata.title);
                    homeSongNameView.setText(player.getCurrentMediaItem().mediaMetadata.title);
                    progessView.setText(getReadableTime((int) player.getCurrentPosition()));
                    duratioView.setText(getReadableTime((int) player.getDuration()));
                    seekbar.setMax((int) player.getDuration());
                    seekbar.setProgress((int) player.getCurrentPosition());

                    playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_outline, 0, 0, 0);
                    homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);

                    //show the current artwork
                    showCurrentArtWork();
                    //update the progress of a current playing song
                    updatePlayerPositionProgress();
                    //load the artwork animation
                    artworkView.setAnimation(loadRotation());

                    //set audio visualizer
                    activateAudioVisualizer();
                    //update play view colors
                    updatePlayerColors();

                } else {
                    playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_outline, 0, 0, 0);
                    homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);

                }
            }
        });

        //skip to next track
        skipNextBtn.setOnClickListener(view -> skipToNextSong());
        homeSkipNextBtn.setOnClickListener(view -> skipToNextSong());

        // skip to previous track
        skipPreviousBtn.setOnClickListener(view -> skipToPreviousSong());
        homeSkipPreviousBtn.setOnClickListener(view -> skipToPreviousSong());


        //play or pause the player
        playPauseBtn.setOnClickListener(view -> playOrPausePlayer());
        homePlayPauseBtn.setOnClickListener(view -> playOrPausePlayer());

        //seek bar listener
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressValue = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                progressValue = seekBar.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player.getPlaybackState() == ExoPlayer.STATE_READY) {
                    seekBar.setProgress(progressValue);
                    progessView.setText(getReadableTime(progressValue));
                    player.seekTo(progressValue);
                }
            }
        });
        //repeat mode
        repeatModeBtn.setOnClickListener(view -> {
            if (repeatMode == 1) {
                //repeat one
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ONE);
                repeatMode = 2;
                repeatModeBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_repeat_one, 0, 0, 0);

            } else if (repeatMode == 2) {
                //shuffe all
                player.setShuffleModeEnabled(true);
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);
                repeatMode = 3;
                repeatModeBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_shuffle, 0, 0, 0);

            } else if (repeatMode == 3) {
                //repeat all
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);
                player.setShuffleModeEnabled(false);
                repeatMode = 1;
                repeatModeBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_repeat_all, 0, 0, 0);

            }
            updatePlayerColors();
        });
    }

    private void playOrPausePlayer() {

        if (player.isPlaying()) {
            player.pause();
            playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_outline, 0, 0, 0);
            homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
            artworkView.clearAnimation();
        } else {
            player.play();
            playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_outline, 0, 0, 0);
            homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);
            artworkView.startAnimation(loadRotation());
        }
        updatePlayerColors();
    }

    private void skipToPreviousSong() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious();
        }
    }

    private void skipToNextSong() {
        if (player.hasNextMediaItem()) {
            player.seekToNext();
        }
    }

    private Animation loadRotation() {
        RotateAnimation rotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(10000);
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        return rotateAnimation;
    }

    private void updatePlayerPositionProgress() {
        new Handler().postDelayed(() -> {
            if (player.isPlaying()) {
                progessView.setText(getReadableTime((int) player.getDuration()));
                seekbar.setProgress((int) player.getCurrentPosition());

                //repeat calling method
                updatePlayerColors();

            }
        }, 1000);
    }

    private void showCurrentArtWork() {
        artworkView.setImageURI(Objects.requireNonNull(player.getCurrentMediaItem()).mediaMetadata.artworkUri);

        if (artworkView.getDrawable() == null) {
            artworkView.setImageResource(R.drawable.default_artwork);
        }
    }

    String getReadableTime(int duration) {
        String time;
        int hrs = duration / (1000 * 60 * 60);
        int min = (duration % (1000 * 60 * 60)) / (1000 * 60);
        int secs = (((duration % (1000 * 60 * 60)) % (1000 * 60 * 60)) % (1000 * 60)) / 1000;


        if (hrs < 1) {
            time = min + ":" + secs;
        } else {
            time = hrs + ":" + min + ":" + secs;
        }
        return time;
    }

    private void showPlayerView() {
        playerView.setVisibility(View.VISIBLE);
        updatePlayerColors();
    }

    private void updatePlayerColors() {
        //only player view is visible
        if (playerView.getVisibility() == View.GONE) {
            return;
        }


        BitmapDrawable bitmapDrawable = (BitmapDrawable) artworkView.getDrawable();
        if (bitmapDrawable == null) {
            bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.default_artwork);
        }
        assert bitmapDrawable != null;
        Bitmap bmp = bitmapDrawable.getBitmap();

        //set bit map to to blur image view
        blurImageView.setImageBitmap(bmp);
        blurImageView.setBlur(4);

        //player control color
        Palette.from(bmp).generate(palette -> {
            if (palette != null) {
                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                if (swatch == null) {
                    swatch = palette.getMutedSwatch();
                    if (swatch == null) {
                        swatch = palette.getDominantSwatch();
                    }
                }
                //extract text color
                int titleTextColor = swatch.getTitleTextColor();
                int bodyTextColor = swatch.getBodyTextColor();
                int rbgColor = swatch.getRgb();

                //set colors to player views
                //status & navigation bar colors
                getWindow().setStatusBarColor(rbgColor);
                getWindow().setNavigationBarColor(rbgColor);

                //more view colors
                songNameView.setTextColor(titleTextColor);
                playerCloseBtn.getCompoundDrawables()[0].setTint(titleTextColor);
                progessView.setTextColor(bodyTextColor);
                duratioView.setTextColor(bodyTextColor);

                repeatModeBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                skipPreviousBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                skipNextBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                playPauseBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                playListBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
            }
        });
    }

    private void exitPlayerView() {
        playerView.setVisibility(View.GONE);
        getWindow().setStatusBarColor(defaultStatusColor);
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199));

    }

    private void userResponnesOnRecordAudioPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(recordAudioPermission)) {
                new AlertDialog.Builder(this)
                        .setTitle("Requesting to show Audio Visualizer")
                        .setMessage("Allow this app to display audio visualizer when is playing")
                        .setPositiveButton("allow", (dialogInterface, i) -> {
                            //request the perm
                            recordAudioPermissionLauncher.launch(recordAudioPermission);

                        }).setNegativeButton("No", (dialogInterface, i) -> {
                    Toast.makeText(getApplicationContext(), "you denied to show the audio visualizer", Toast.LENGTH_SHORT).show();
                    dialogInterface.dismiss();
                }).show();
            } else {
                Toast.makeText(getApplicationContext(), "you denied to show the audio visualizer", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //audio visualizer
    private void activateAudioVisualizer() {
        if (ContextCompat.checkSelfPermission(this, recordAudioPermission) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        audioVisualizer.setColor(ContextCompat.getColor(this, R.color.secondary_color));
        audioVisualizer.setDensity(100);
        audioVisualizer.setPlayer(player.getAudioSessionId());
    }


    //  ------------------------------------------------------------------------------------------------------------------

    private void showSongs(List<Song> songs) {

        if (songs.size() == 0) {
            Toast.makeText(this, "No Songs", Toast.LENGTH_SHORT).show();
            return;
        }

        //save songs
        allSongs.clear();
        allSongs.addAll(songs);

        //update the toolbar title
        String title = getResources().getString(R.string.app_name) + " - " + songs.size();
        Objects.requireNonNull(getSupportActionBar()).setTitle(title);

        //Layout Manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        //song adapter
        songAdapter = new SongAdapter(this, songs, player, playerView);
        //set adapter to recyclerview
        recyclerView.setAdapter(songAdapter);

        //recyclerview animators optional
        ScaleInAnimationAdapter scaleInAnimationAdapter = new ScaleInAnimationAdapter(songAdapter);
        scaleInAnimationAdapter.setDuration(1000);
        scaleInAnimationAdapter.setInterpolator(new OvershootInterpolator());
        scaleInAnimationAdapter.setFirstOnly(false);
        recyclerView.setAdapter(scaleInAnimationAdapter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //release the player
//        if (player.isPlaying()) {
//            player.stop();
//        }
        doUnbindService();
    }

    private void doUnbindService() {
        if(isBound){
            unbindService(playerServiceConnection);
            isBound =false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_btn, menu);

        //search button item
        MenuItem menuItem = menu.findItem(R.id.searchBtn);
        SearchView searchView = (SearchView) menuItem.getActionView();

        //search song method
        SearchSong(searchView);

        return super.onCreateOptionsMenu(menu);
    }

    private void SearchSong(SearchView searchView) {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //filter songs
                filterSongs(newText.toLowerCase());
                return true;
            }
        });
    }

    private void userResponnes() {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            //fetch songs
            fetchSongs();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(permission)) {

                new AlertDialog.Builder(this)
                        .setTitle("Requesting Permission")
                        .setMessage("Allow us to fetch songs on your device")
                        .setPositiveButton("allow", (dialogInterface, i) -> {
                            //request permission
                            storagePermissionLauncher.launch(permission);
                        }).setNegativeButton("Canel", (dialogInterface, i) -> {
                    Toast.makeText(getApplicationContext(), "You denied us to show songs", Toast.LENGTH_SHORT).show();
                    dialogInterface.dismiss();
                })
                        .show();
            }

        } else {
            Toast.makeText(this, "You caneled to show songs", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchSongs() {
        //definde a list to carry songs
        List<Song> songs = new ArrayList<>();
        Uri mediaStoreUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        }

        //define projection
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,

        };

        //order
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        //get the songs
        try (Cursor cursor = getContentResolver().query(mediaStoreUri, projection, null, null, sortOrder)) {
            //cache cursor indices
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

            //clear the previous loaded  before adding loading again
            while (cursor.moveToNext()) {
                //get the values of column for a given audio file
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                int duration = cursor.getInt(durationColumn);
                int size = cursor.getInt(sizeColumn);
                long albumId = cursor.getLong(albumIdColumn);

                //song uri
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                //albumn artwork uri
                Uri albumnArtWorkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);


                //song item
                Song song = new Song(name, uri, albumnArtWorkUri, size, duration);

                //remove .mp3 extension from the song's name
                if (!TextUtils.isEmpty(name) && name.contains("\\.")) {
                    name = name.substring(0, name.lastIndexOf("."));
                }

                //add song item  to song list\
                songs.add(song);
            }
            //display songs
            showSongs(songs);
        }
    }

    private void filterSongs(String query) {
        List<Song> filteredList = new ArrayList<>();

        if (allSongs.size() > 0) {
            for (Song song : allSongs) {
                if (song.getTitle().toLowerCase().contains(query)) {
                    filteredList.add(song);
                }
            }

            if (songAdapter != null) {
                songAdapter.filterSongs(filteredList);
            }
        }
    }
}