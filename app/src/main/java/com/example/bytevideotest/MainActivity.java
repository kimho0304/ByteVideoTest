package com.example.bytevideotest;

import static androidx.documentfile.provider.DocumentFile.fromSingleUri;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private int portNum, deviceId, baudRate;
    int totalSize = 0;
    public Uri selectedFile;
    private Handler mainHandler;
    private static final int REQ_CODE = 123; // startActivityForResult에 쓰일 사용자 정의 요청 코드
    private Thread transport = null;
    private Socket socket;
    private String ip = "192.168.0.22";
    private int port = 9090;
    Context context = null;
    long totalRead = 0;
    long cnt;
    int bytesToRead = 50 * 1024;
    // UI variables definition ↓
    private Button send_btn, select_file_btn;
    private VideoView videoView, videoView2;
    private TextView directoryText;
    private int mPlayerPosition;
    private File mBufferFile;

    // https://stackoverflow.com/a/21549067
    private class GetYoutubeFile extends Thread {
        private String mUrl;
        private String mFile;
        public GetYoutubeFile() {

        }

        @Override
        public void run() {
            mainHandler.post(() -> { // 메인 Handler를 통해 메인 스레드에 "Server is offline." 내용의 Toast Message를 띄움.
            try {
                Log.i("GetYoutubeFile", "Start the func.");

                // 출력물 파일을 담을 File 형 변수 bufferFile 생성.
                File bufferFile = File.createTempFile("GetYoutubeFile", "mp4");

                //
                BufferedOutputStream bufferOS = new BufferedOutputStream(new FileOutputStream(bufferFile));

                // 선택된 파일을 InputStream 변수로 초기화.
                InputStream is = getContentResolver().openInputStream(selectedFile);
                // 생성자( 대상 InputStream, 버퍼의 크기 설정)
                BufferedInputStream bIS = new BufferedInputStream(is, 2048);

                /*
                InputStream is1 = getContentResolver().openInputStream(selectedFile);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                for (int data = is1.read(); data != -1; data = is1.read()) {
                    Log.i("GetYoutubeFile", "data: " + data);
                    //byteArrayOutputStream.write(data);
                }
                Log.i("GetYoutubeFile", "is: " + byteArrayOutputStream.toString());
                */

                byte[] buffer = new byte[16384]; // == 2^14
                int numRead;
                boolean started = false;
                cnt = 0;
                // 데이터의 다음 바이트를 반환하거나 파일 끝에 도달하면 -1을 반환.
                while ((numRead = bIS.read(buffer)) != -1) {
                    cnt++;
                    // buffer[offset(0)]부터 numRead만큼의 바이트를 bufferOS에 씀.
                    totalRead += numRead;
                    //if((cnt%2)==0) continue;

                    bufferOS.write(buffer, 0, numRead);
                    Log.i("GetYoutubeFile", "numRead: "+numRead+", cnt: "+cnt);
                    // 버퍼가 모두 채워지거나 close(), flush() 호출 → 버퍼의 모든 내용을 파일에 출력.
                    bufferOS.flush();

                        Log.i("GetYoutubeFile", "test, "+cnt);
                        //Log.i("GetYoutubeFile", "path: " + bufferFile.getAbsolutePath());
                        mPlayerPosition = videoView2.getCurrentPosition();
                        videoView2.setVideoPath(bufferFile.getAbsolutePath());
                        videoView2.start();
                    /*if(cnt > 10){
                        setSourceAndStartPlay(bufferFile);
                        Log.i("GetYoutubeFile", "BufferHIT:StartPlay");
                        started = true;
                        break;
                    }*/
                    //setSourceAndStartPlay(bufferFile);
                    if (totalRead >= totalSize && !started) {
                        //setSourceAndStartPlay(bufferFile);
                        Log.i("GetYoutubeFile", "BufferHIT:StartPlay");
                        started = true;
                    }
                }
                Log.i("GetYoutubeFile", "The end of the loop.");
                mBufferFile = bufferFile;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            });
        }
    }

    public void setSourceAndStartPlay(File bufferFile) {
        try {
            mainHandler.post(() -> { // 메인 Handler를 통해 메인 스레드에 "Server is offline." 내용의 Toast Message를 띄움.
                Log.i("GetYoutubeFile", "test, "+cnt);
                //Log.i("GetYoutubeFile", "path: " + bufferFile.getAbsolutePath());
                mPlayerPosition = videoView2.getCurrentPosition();
                videoView2.setVideoPath(bufferFile.getAbsolutePath());
                videoView2.start();
            });
        } catch (IllegalArgumentException e) {
            Log.e("GetYoutubeFile_Err", "IllegalArgumentException");
            e.printStackTrace();
        } catch (IllegalStateException e) {
            Log.e("GetYoutubeFile_Err", "IllegalStateException");
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        select_file_btn = findViewById(R.id.selectFileBtn);

        videoView = findViewById(R.id.videoView);
        videoView2 = findViewById(R.id.videoView2);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { // 비디오 리스너 등록.
            @Override
            public void onPrepared(MediaPlayer mp) {
                // 준비 완료되면 비디오 재생.
                mp.setLooping(true); // 비디오 무한루프 설정: true.
                mp.start(); // 비디오 재생 시작.
            }
        });

        mainHandler = new Handler(Looper.getMainLooper());

        directoryText = findViewById(R.id.fileDirectoryText);

        select_file_btn = findViewById(R.id.selectFileBtn); // 파일 선택 버튼 등록.
        select_file_btn.setOnClickListener(view -> {
                    openFile(); //openFile() 실행.
                }
        );

        send_btn = findViewById(R.id.sendBtn); // 데이터 전송 버튼 등록.
        send_btn.setOnClickListener(view -> {
                    new GetYoutubeFile().start();
                }
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) { // startActivityForResult 실행 후, 값을 전달 받는 액티비티 함수. startActivityForResult 실행 후 파일 탐색기 액티비티에서 파일을 선택 하면, 이 선택 된 파일이 전달할 값이 됨.
        // 그러므로 onActivityResult에 이 선택 된 파일을 전달함.
        super.onActivityResult(requestCode, resultCode, data); // 생성자 호출
        Log.i("videocrypto", "onActivityResult");

        if (requestCode == REQ_CODE && resultCode == Activity.RESULT_OK) { // 요청 코드가 사용자가 정의한 값이랑 동일하고, 결과가 OK 상태(정상적)인 경우:
            if (data != null) { // 전달 받은 값(파일 탐색기에서 선택한 파일)이 존재할 경우:
                selectedFile = data.getData(); // URI 객체인 selectedFile에 그 값을 할당.
                String filename = queryName(getContentResolver(), selectedFile);
                Log.i("videocrypto_filename", filename);

                DocumentFile fileName = fromSingleUri(this, selectedFile); // URI 객체인 selectedFile을 새로운 DocumentFile형 객체에 할당. URI 객체의 경우 파일의 원본 정보가 아닌 새로 래핑된 정보만 제공하므로,

                // 원본 정보를 볼 수 있는 DocumentFile 객체에 새로 할당한다.
                directoryText.setText(fileName.getName()); // fileName의 이름, 즉 원본 파일명을 directoryText에 할당.

                totalSize = Integer.parseInt(getRealSizeFromUri(this, selectedFile));
                Log.i("GetYoutubeFile", "size: " + totalSize);

                //Log.i("GetYoutubeFile", "Size of file: " + getRealSizeFromUri(this, selectedFile));
                /*
                try {
                } catch (IOException e) {
                    Log.e("transportThread_onActivityResult", "transportThread err");
                    e.printStackTrace();
                }*/

                videoView.setVideoURI(selectedFile); // 비디오뷰에 선택된 비디오 파일을 전달, 재생.
            }
        }
    }

    // https://stackoverflow.com/questions/45589736/uri-file-size-is-always-0
    private String getRealSizeFromUri(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Audio.Media.SIZE};
            cursor = context.getContentResolver().query(uri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); // 파일 탐색기를 여는 intent 등록
        intent.addCategory(Intent.CATEGORY_OPENABLE); //onActivityResult 내에서 사용할 ContentProvider로 URI 객체를 접근하기 위해 쓰이는 intent 등록.
        //ContetProvider는 app 사이에서 data를 공유하는 역할을 함. ContentResolver는 ContentProvider를 통해 데이터에 접근해서 값을 가져옴.
        intent.setType("*/*"); //파일 형식 지정: *은 모든 타입을 의미

        startActivityForResult(intent, REQ_CODE); //위의 intent를 통해 파일 탐색 액티비티 실행.
    }

    private String queryName(ContentResolver resolver, Uri uri) { // https://stackoverflow.com/a/38304115
        Cursor returnCursor = resolver.query(uri, null, null, null, null);
        assert returnCursor != null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();

        String[] result = name.split("\\."); // '.'을 기준으로 확장명 분리 ex) ".txt"
        return result[0];
    }
}