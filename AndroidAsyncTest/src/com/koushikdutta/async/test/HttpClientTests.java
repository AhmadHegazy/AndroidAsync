package com.koushikdutta.async.test;

import java.io.File;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.util.Log;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.*;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import junit.framework.Assert;
import junit.framework.TestCase;
import android.os.Environment;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient.DownloadCallback;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;

public class HttpClientTests extends TestCase {
    AsyncHttpClient client;
    AsyncServer server = new AsyncServer();
    
    public HttpClientTests() {
        super();
        server.setAutostart(true);
        client = new AsyncHttpClient(server);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        client.getSSLSocketMiddleware().setConnectAllAddresses(false);
        client.getSocketMiddleware().setConnectAllAddresses(false);
        server.stop();
    }

    public void testConnectAllAddresses() throws Exception {
        assertEquals(client.getSSLSocketMiddleware().getConnectionPoolCount(), 0);
        assertEquals(client.getSocketMiddleware().getConnectionPoolCount(), 0);

        client.getSSLSocketMiddleware().setConnectAllAddresses(true);
        client.getSocketMiddleware().setConnectAllAddresses(true);

        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        AsyncHttpGet get = new AsyncHttpGet("http://www.clockworkmod.com");
        get.setLogging("ConnectionPool", Log.VERBOSE);
        client.execute(get, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                // make sure gzip decoding works, as that is generally what github sends.
                Assert.assertEquals("gzip", response.getHeaders().getContentEncoding());
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        md5.update(bb);
                    }
                });

                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        semaphore.release();
                    }
                });
            }
        });

        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));

        long start = System.currentTimeMillis();
        while (client.getSocketMiddleware().getConnectionPoolCount() != 2) {
            Thread.sleep(50);
            if (start + 5000L < System.currentTimeMillis())
                fail();
        }
    }

    private static final long TIMEOUT = 10000L;
    public void testHomepage() throws Exception {
        Future<String> ret = client.get("http://google.com", (StringCallback)null);
        assertNotNull(ret.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    public void testClockworkMod() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        client.execute("http://www.clockworkmod.com", new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                // make sure gzip decoding works, as that is generally what github sends.
                Assert.assertEquals("gzip", response.getHeaders().getContentEncoding());
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        md5.update(bb);
                    }
                });

                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        semaphore.release();
                    }
                });
            }
        });

        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    // this testdata file was generated using /dev/random. filename is also the md5 of the file.
    final static String dataNameAndHash = "6691924d7d24237d3b3679310157d640";
    final static String githubPath = "github.com/koush/AndroidAsync/raw/master/AndroidAsyncTest/testdata/";
    final static String github = "https://" + githubPath + dataNameAndHash;
    final static String githubInsecure = "http://" + githubPath + dataNameAndHash;
    public void testGithubRandomData() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        AsyncHttpGet get = new AsyncHttpGet(github);
        get.setLogging("AsyncTest", Log.DEBUG);
        client.execute(get, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                assertNull(ex);
                // make sure gzip decoding works, as that is generally what github sends.
                Assert.assertEquals("gzip", response.getHeaders().getContentEncoding());
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        md5.update(bb);
                    }
                });
                
                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        semaphore.release();
                    }
                });
            }
        });
        
        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }
    
    public void testGithubRandomDataWithFuture() throws Exception {
        final Md5 md5 = Md5.createInstance();
        Future<ByteBufferList> bb = client.get(github, (DownloadCallback)null);
        md5.update(bb.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }

    public void testInsecureGithubRandomDataWithFuture() throws Exception {
        final Md5 md5 = Md5.createInstance();
        Future<ByteBufferList> bb = client.get(githubInsecure, (DownloadCallback)null);
        md5.update(bb.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }

    public void testInsecureGithubRandomDataWithFutureCallback() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        client.executeByteBufferList(new AsyncHttpGet(githubInsecure).setHandler(null), null).setCallback(new FutureCallback<ByteBufferList>() {
            @Override
            public void onCompleted(Exception e, ByteBufferList bb) {
                md5.update(bb);
                semaphore.release();
            }
        });
        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }

    public void testGithubHelloWithFuture() throws Exception {
        Future<String> string = client.get("https://" + githubPath + "hello.txt", (StringCallback)null);
        assertEquals(string.get(TIMEOUT, TimeUnit.MILLISECONDS), "hello world");
    }

    public void testGithubHelloWithFutureCallback() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        client.executeString(new AsyncHttpGet("https://" + githubPath + "hello.txt").setHandler(null))
        .setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String result) {
                assertEquals(result, "hello world");
                semaphore.release();
            }
        });
        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    Future<String> future;
    public void testCancel() throws Exception {
        future = AsyncHttpClient.getDefaultInstance().get("http://yahoo.com", new StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                fail();
            }
            
            @Override
            public void onConnect(AsyncHttpResponse response) {
                future.cancel();
            }
        });

        try {
            future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            // this should never reach here as it was cancelled
            fail();
        }
        catch (TimeoutException e) {
            // timeout should also fail, since it was cancelled
            fail();
        }
        catch (ExecutionException e) {
            // execution exception is correct, make sure inner exception is cancellation
            assertTrue(e.getCause() instanceof CancellationException);
        }
    }
    
    public void testCache() throws Exception {
        ResponseCacheMiddleware cache = ResponseCacheMiddleware.addCache(client, new File(Environment.getExternalStorageDirectory(), "AndroidAsyncTest"), 1024 * 1024 * 10);
        try {
            // clear the old cache
            cache.clear();
            // populate the cache
            testGithubRandomData();
            // this should result in a conditional cache hit
            testGithubRandomData();
            assertEquals(cache.getConditionalCacheHitCount(), 1);
        }
        finally {
            client.getMiddleware().remove(cache);
        }
    }

    Future<File> fileFuture;
    public void testFileCancel() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        fileFuture = client.getFile(github, "/sdcard/hello.txt", new AsyncHttpClient.FileCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, File result) {
                fail();
            }

            @Override
            public void onProgress(AsyncHttpResponse response, int downloaded, int total) {
                semaphore.release();
            }
        })
        .setCallback(new FutureCallback<File>() {
            @Override
            public void onCompleted(Exception e, File result) {
                fail();
            }
        });

        try {
            assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
            assertTrue(fileFuture.cancel());
            fileFuture.get();
            fail();
        }
        catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof CancellationException);
        }
//        Thread.sleep(1000);
//        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(new File("/sdcard/hello.txt").exists());
    }
}
