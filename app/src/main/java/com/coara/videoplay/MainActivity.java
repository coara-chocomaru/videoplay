package com.coara.videoplay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_PICK_VIDEO = 1;
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    private static final int CONTROLS_HIDE_DELAY = 3000; 
    private static final int SKIP_INTERVAL = 3000;

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private boolean surfaceReady = false;

    private LinearLayout controlsOverlay;
    private Button btnSelect, btnPlayPause, btnScreenLock;
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvDuration;
    private Handler handler = new Handler();
    private Runnable hideControlsRunnable;
    private boolean isControlsVisible = true;
    private boolean wasPlaying = false;
    private int playbackPosition = 0;
    private Uri videoUri = null;
    private int currentBufferedPercent = 100; 
    private boolean isScreenLocked = false;
    private UpdateSeekBarThread seekBarThread = null;
    private int pendingSeekPosition = -1;
    private boolean initialPlaybackDelay = false;
    private Runnable delayedPlaybackRunnable = null;
    private class UpdateSeekBarThread extends Thread {
        private volatile boolean running = true;
        @Override
        public void run() {
            while (running) {
                if (mediaPlayer != null) {  
                    final int pos = mediaPlayer.getCurrentPosition();
                    final int duration = mediaPlayer.getDuration();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            seekBar.setProgress(pos);
                            if (duration > 0) {
                                seekBar.setSecondaryProgress((int)(duration * currentBufferedPercent / 100.0));
                            }
                            updateTimeTexts();
                        }
                    });
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        public void stopThread() {
            running = false;
            this.interrupt();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        surfaceView   = (SurfaceView) findViewById(R.id.surfaceView);
        controlsOverlay = (LinearLayout) findViewById(R.id.controlsOverlay);
        btnSelect     = (Button) findViewById(R.id.btnSelect);
        btnPlayPause  = (Button) findViewById(R.id.btnPlayPause);
        btnScreenLock = (Button) findViewById(R.id.btnScreenLock);
        seekBar       = (SeekBar) findViewById(R.id.seekBar);
        tvCurrentTime = (TextView) findViewById(R.id.tvCurrentTime);
        tvDuration    = (TextView) findViewById(R.id.tvDuration);

        btnPlayPause.setEnabled(false);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceReady = true;
                if (videoUri != null) {
                    initializeVideo();
                }
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                if (mediaPlayer != null) {
                    playbackPosition = mediaPlayer.getCurrentPosition();
                    wasPlaying = mediaPlayer.isPlaying();
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            }
        });

        
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    int width = v.getWidth();
                    float x = event.getX();
                    float leftCenterBound = width * 0.33f;
                    float rightCenterBound = width * 0.67f;
                    if (x >= leftCenterBound && x <= rightCenterBound) {
                        showControls();
                    } else if (mediaPlayer != null) {
                        if (x < leftCenterBound) {
                            skipBackward();
                        } else {
                            skipForward();
                        }
                    } else {
                        toggleControlsVisibility();
                    }
                    return true;
                }
                return false;
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showControls();
                togglePlayPause();
            }
        });

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectVideo();
            }
        });

        btnScreenLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleScreenLock();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    updateTimeTexts();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(hideControlsRunnable);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleHideControls();
            }
        });

        hideControlsRunnable = new Runnable() {
            @Override
            public void run() {
                hideControls();
            }
        };

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CODE_PERMISSIONS);
        }
    }

    
    private void skipBackward() {
        if (mediaPlayer != null) {
            final boolean wasPlayingLocal = mediaPlayer.isPlaying();
            int currentPos = mediaPlayer.getCurrentPosition();
            int targetPos = currentPos - SKIP_INTERVAL;
            if (targetPos < 0) {
                targetPos = 0;
            }
            mediaPlayer.seekTo(targetPos);
            updateTimeTexts();
            
            if (wasPlayingLocal) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                        }
                    }
                }, 50);
            }
            showControls();
        }
    }


    private void skipForward() {
        if (mediaPlayer != null) {
            final boolean wasPlayingLocal = mediaPlayer.isPlaying();
            int currentPos = mediaPlayer.getCurrentPosition();
            int duration = mediaPlayer.getDuration();
            int targetPos = currentPos + SKIP_INTERVAL;
            if (targetPos > duration) {
                targetPos = duration;
            }
            mediaPlayer.seekTo(targetPos);
            updateTimeTexts();
            if (wasPlayingLocal) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                        }
                    }
                }, 50);
            }
            showControls();
        }
    }

    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            playbackPosition = 0;
            wasPlaying = false;
            currentBufferedPercent = 100;
            videoUri = data.getData();
            initialPlaybackDelay = true;
            btnPlayPause.setEnabled(false);
            btnPlayPause.setText("バッファ取得中…");
            if (surfaceReady) {
                initializeVideo();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initializeVideo() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, videoUri);
            mediaPlayer.setDisplay(surfaceHolder);
    
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){
                @Override
                public void onPrepared(MediaPlayer mp) {
                    int duration = mediaPlayer.getDuration();
                    if (duration <= 0) {
                        Toast.makeText(MainActivity.this, "選択されたファイルは動画として再生できません", Toast.LENGTH_SHORT).show();
                        mediaPlayer.stop();
                        btnPlayPause.setEnabled(false);
                        return;
                    }
                    adjustSurfaceViewSize(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
                    seekBar.setMax(duration);
                    tvDuration.setText(formatTime(duration));
                    currentBufferedPercent = 100;
                    mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener(){
                        @Override
                        public void onBufferingUpdate(MediaPlayer mp, int percent) {
                            currentBufferedPercent = percent;
                        }
                    });
                    mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener(){
                        @Override
                        public boolean onInfo(MediaPlayer mp, int what, int extra) {
                            return false;
                        }
                    });
                    if (playbackPosition > 0) {
                        mediaPlayer.seekTo(playbackPosition);
                    }
                    
                    if (initialPlaybackDelay) {
                        
                        mediaPlayer.start();
                        
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.pause();
                                mediaPlayer.seekTo(0);
                            }
                        }, 100);
                        
                        delayedPlaybackRunnable = new Runnable() {
                            @Override
                            public void run() {
                                btnPlayPause.setEnabled(true);
                                btnPlayPause.setText("再生");
                                if (seekBarThread != null) {
                                    seekBarThread.stopThread();
                                }
                                seekBarThread = new UpdateSeekBarThread();
                                seekBarThread.start();
                                initialPlaybackDelay = false;
                                delayedPlaybackRunnable = null;
                            }
                        };
                        handler.postDelayed(delayedPlaybackRunnable, 3000);
                    } else {
                        if (wasPlaying) {
                            mediaPlayer.start();
                            btnPlayPause.setText("停止");
                        } else {
                            btnPlayPause.setText("再生");
                        }
                        if (seekBarThread != null) {
                            seekBarThread.stopThread();
                        }
                        seekBarThread = new UpdateSeekBarThread();
                        seekBarThread.start();
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
                @Override
                public void onCompletion(MediaPlayer mp) {
                    btnPlayPause.setText("再生");
                    if (seekBarThread != null) {
                        seekBarThread.stopThread();
                        seekBarThread = null;
                    }
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener(){
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Toast.makeText(MainActivity.this, "動画再生中にエラーが発生しました", Toast.LENGTH_SHORT).show();
                    btnPlayPause.setEnabled(false);
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "動画として再生できないファイルです", Toast.LENGTH_SHORT).show();
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            btnPlayPause.setEnabled(false);
        }
    }

    private void adjustSurfaceViewSize(int videoWidth, int videoHeight) {
        if (videoWidth == 0 || videoHeight == 0) return;
        int viewWidth = surfaceView.getWidth();
        int viewHeight = surfaceView.getHeight();
        float videoAspect = (float) videoWidth / videoHeight;
        float viewAspect = (float) viewWidth / viewHeight;
        int newWidth, newHeight;
        if (viewAspect > videoAspect) {
            newHeight = viewHeight;
            newWidth = (int)(viewHeight * videoAspect);
        } else {
            newWidth = viewWidth;
            newHeight = (int)(viewWidth / videoAspect);
        }
        android.view.ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
        lp.width = newWidth;
        lp.height = newHeight;
        surfaceView.setLayoutParams(lp);
    }

    private void togglePlayPause() {
        if (videoUri == null) {
            Toast.makeText(MainActivity.this, "動画ファイルが選択されていません", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setText("再生");
                if (seekBarThread != null) {
                    seekBarThread.stopThread();
                    seekBarThread = null;
                }
            } else {
                if (delayedPlaybackRunnable != null) {
                    handler.removeCallbacks(delayedPlaybackRunnable);
                    delayedPlaybackRunnable = null;
                    initialPlaybackDelay = false;
                }
                mediaPlayer.start();
                btnPlayPause.setText("停止");
                if (seekBarThread != null) {
                    seekBarThread.stopThread();
                }
                seekBarThread = new UpdateSeekBarThread();
                seekBarThread.start();
            }
        }
    }

    private void toggleScreenLock() {
        if (!isScreenLocked) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            isScreenLocked = true;
            btnScreenLock.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
            Toast.makeText(MainActivity.this, "画面固定中", Toast.LENGTH_SHORT).show();
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            isScreenLocked = false;
            btnScreenLock.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            Toast.makeText(MainActivity.this, "画面固定解除", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateTimeTexts() {
        if (mediaPlayer != null) {
            int current = mediaPlayer.getCurrentPosition();
            tvCurrentTime.setText(formatTime(current));
        }
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
    
    private void toggleControlsVisibility() {
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void hideControls() {
        controlsOverlay.setVisibility(View.GONE);
        isControlsVisible = false;
    }

    private void showControls() {
        controlsOverlay.setVisibility(View.VISIBLE);
        isControlsVisible = true;
        scheduleHideControls();
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            playbackPosition = mediaPlayer.getCurrentPosition();
            wasPlaying = mediaPlayer.isPlaying();
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setText("再生");
            }
        }
        if (seekBarThread != null) {
            seekBarThread.stopThread();
            seekBarThread = null;
        }
        handler.removeCallbacks(hideControlsRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoUri != null && surfaceReady) {
            initializeVideo();
        }
    }
}
