package com.example.audiotest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    public static final int SAMPLE_RATE = 44100; // supported on all devices
    public static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT; // not supported on all devices
    public static final int BUFFER_SIZE_RECORDING = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    public static final int BUFFER_SIZE_PLAYING = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    MediaRecorder mediaRecorder;
    MediaPlayer mediaPlayer;
    AudioRecord audioRecord;
    AudioTrack audioTrack;

    private Thread recordingThread;
    private Thread playingThread;

    Button recordMediaRecorder;
    Button playMediaPlayer;
    Button recordAudioRecord;
    Button playAudioTrack;

    boolean isRecordingMedia = false;
    boolean isPlayingMedia = false;
    boolean isRecordingAudio = false;
    boolean isPlayingAudio = false;


    String fileNameMedia;
    String fileNameAudio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // find views
        playMediaPlayer = findViewById(R.id.play_mediaplayer);
        recordMediaRecorder = findViewById(R.id.record_mediarecorder);
        playAudioTrack = findViewById(R.id.play_audiotrack);
        recordAudioRecord = findViewById(R.id.record_audiorecord);

        fileNameMedia = getFilesDir().getPath() + "/testfile" + ".3gp"; // store audio files in internal storage
        fileNameAudio = getFilesDir().getPath() + "/testfile" + ".pcm";

        File fileMedia = new File(fileNameMedia);
        File fileAudio = new File(fileNameAudio);
        if (!fileMedia.exists() || fileAudio.exists()) { // create empty files if needed
            try {
                fileMedia.createNewFile();
                fileAudio.createNewFile();
            }
            catch (IOException e) {
                Log.d(TAG, "could not create file " + e.toString());
                e.printStackTrace();
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
        }

        setListeners();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { // handle user response to permission request
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission to record audio granted", Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_LONG).show();
            playMediaPlayer.setEnabled(false);
            recordMediaRecorder.setEnabled(false);
            playAudioTrack.setEnabled(false);
            recordAudioRecord.setEnabled(false);
        }
    }

    private void setListeners() { // start or stop recording and playback depending on state
        recordMediaRecorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecordingMedia) {
                    startRecording(recordMediaRecorder.getId()); // pass id so we can deal with MediaRecorder and AudioRecord separately
                }
                else {
                    stopRecording(recordMediaRecorder.getId());
                }
                isRecordingMedia = !isRecordingMedia;
                setButtonText();
            }
        });

        recordAudioRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecordingAudio) {
                    startRecording(recordAudioRecord.getId());
                }
                else {
                    stopRecording(recordAudioRecord.getId());
                }
                setButtonText();
            }
        });

        playMediaPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlayingMedia) {
                    startPlaying(playMediaPlayer.getId());
                }
                else {
                    stopPlaying(playMediaPlayer.getId());
                }
                isPlayingMedia = !isPlayingMedia;
                setButtonText();
            }
        });

        playAudioTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlayingAudio) {
                    startPlaying(playAudioTrack.getId());
                }
                else {
                    stopPlaying(playAudioTrack.getId());
                }
                setButtonText();
            }
        });

    }

    private void startRecording(int id) {
        if (id == R.id.record_mediarecorder) { // record with MediaPlayer
            if (mediaRecorder == null) { // safety check, don't start a new recording if one is already going
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFile(fileNameMedia);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);

                try {
                    mediaRecorder.prepare();
                } catch (IOException e) {
                    // handle error
                    Toast.makeText(this, "IOException while trying to prepare MediaRecorder", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "could not prepare MediaRecorder " + e.toString());
                    return;
                } catch (IllegalStateException e) {
                    // handle error
                    Toast.makeText(this, "IllegalStateException while trying to prepare MediaRecorder", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "could not prepare MediaRecorder " + e.toString());
                    return;
                }

                mediaRecorder.start();
                Log.d(TAG, "recording started with MediaRecorder");
            }
        }
        else { // record with AudioRecord
            if (audioRecord == null) { // safety check
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, BUFFER_SIZE_RECORDING);

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
                    Log.e(TAG, "error initializing AudioRecord");
                    Toast.makeText(this, "Couldn't initialize AudioRecord, check configuration", Toast.LENGTH_SHORT).show();
                    return;
                }

                audioRecord.startRecording();
                Log.d(TAG, "recording started with AudioRecord");

                isRecordingAudio = true;

                recordingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        writeAudioDataToFile();
                    }
                });
                recordingThread.start();
            }
        }
    }

    private void stopRecording(int id) {
        if (id == R.id.record_mediarecorder) { // stop recording with MediaRecorder
            if (mediaRecorder != null) {
                // stop recording and free up resources
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();

                mediaRecorder = null;
            }
        }
        else { // stop recording with AudioRecord
            if (audioRecord != null) {
                isRecordingAudio = false; // triggers recordingThread to exit while loop
            }
        }
    }

    private void startPlaying(int id) {
        if (id == R.id.play_mediaplayer) { // play with MediaPlayer
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();

                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { // release resources when end of file is reached
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.reset();
                        mp.release();
                        mediaPlayer = null;
                        isPlayingMedia = false;
                        setButtonText();
                    }
                });

                try {
                    mediaPlayer.setDataSource(fileNameMedia);
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()); // optional step
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    Log.d(TAG, "playback started with MediaPlayer");
                } catch (IOException e) {
                    Toast.makeText(this, "Couldn't prepare MediaPlayer, IOException", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "error reading from file while preparing MediaPlayer" + e.toString());
                } catch (IllegalArgumentException e) {
                    Toast.makeText(this, "Couldn't prepare MediaPlayer, IllegalArgumentException", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "illegal argument given " + e.toString());
                }
            }
        }
        else { // use AudioTrack
            if (audioTrack == null) {
                audioTrack = new AudioTrack(
                        new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build(),
                        new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        BUFFER_SIZE_PLAYING,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                );

                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Toast.makeText(this, "Couldn't initialize AudioTrack, check configuration", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "error initializing AudioTrack");
                    return;
                }

                audioTrack.play();
                Log.d(TAG, "playback started with AudioTrack");

                isPlayingAudio = true;

                playingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        readAudioDataFromFile();
                    }
                });
                playingThread.start();
            }
        }
    }

    private void stopPlaying(int id) {
        if (id == R.id.play_mediaplayer) {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
        else {
            isPlayingAudio = false; // will trigger playingThread to exit while loop
        }
    }

    private void writeAudioDataToFile() { // called inside Runnable of recordingThread

        byte[] data = new byte[BUFFER_SIZE_RECORDING/2]; // assign size so that bytes are read in in chunks inferior to AudioRecord internal buffer size

        FileOutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(fileNameAudio);
        } catch (FileNotFoundException e) {
            // handle error
            Toast.makeText(this, "Couldn't find file to write to", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "file not found for file name " + fileNameAudio + ", " + e.toString());
            return;
        }

        while (isRecordingAudio) {
            int read = audioRecord.read(data, 0, data.length);
            try {
                outputStream.write(data, 0, read);
            }
            catch (IOException e) {
                Toast.makeText(this, "Couldn't write to file while recording", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "IOException while recording with AudioRecord, " + e.toString());
                e.printStackTrace();
            }
        }

        try { // clean up file writing operations
            outputStream.flush();
            outputStream.close();
        }
        catch (IOException e) {
            Log.e(TAG, "exception while closing output stream " + e.toString());
            e.printStackTrace();
        }

        audioRecord.stop();
        audioRecord.release();

        audioRecord = null;
        recordingThread = null;
    }

    private void readAudioDataFromFile() { // called inside Runnable of playingThread

        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(fileNameAudio);
        }
        catch (IOException e) {
            Toast.makeText(this, "Couldn't open file input stream, IOException", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "could not create input stream before using AudioTrack " + e.toString());
            e.printStackTrace();
            return;
        }
        byte[] data = new byte[BUFFER_SIZE_PLAYING/2];
        int i = 0;

        while (isPlayingAudio && (i != -1)) { // continue until run out of data or user stops playback
            try {
                i = fileInputStream.read(data);
                audioTrack.write(data, 0, i);
            }
            catch (IOException e) {
                Toast.makeText(this, "Couldn't read from file while playing audio, IOException", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Could not read data " + e.toString());
                e.printStackTrace();
                return;
            }

        }
        try { // finish file operations
            fileInputStream.close();
        }
        catch (IOException e) {
            Log.e(TAG, "Could not close file input stream " + e.toString());
            e.printStackTrace();
            return;
        }

        // clean up resources
        isPlayingAudio = false;
        setButtonText();
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
        playingThread = null;

    }

    private void setButtonText() { // UI updates for button text
        if (isRecordingMedia) {
            recordMediaRecorder.setText(R.string.record_button_stop);
        }
        else {
            recordMediaRecorder.setText(R.string.record_mediarecorder);
        }
        if (isRecordingAudio) {
            recordAudioRecord.setText(R.string.record_button_stop);
        }
        else {
            recordAudioRecord.setText(R.string.record_audiorecord);
        }
        if (isPlayingMedia) {
            playMediaPlayer.setText(R.string.play_button_stop);
        }
        else {
            playMediaPlayer.setText(R.string.play_mediaplayer);
        }
        if (isPlayingAudio) {
            playAudioTrack.setText(R.string.play_button_stop);
        }
        else {
            playAudioTrack.setText(R.string.play_audiotrack);
        }
    }

}