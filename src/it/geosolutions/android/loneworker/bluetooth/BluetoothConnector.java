/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.geosolutions.android.loneworker.bluetooth;

import it.geosolutions.android.loneworker.BuildConfig;
import it.geosolutions.android.loneworker.service.BluetoothService;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections and a thread for reading out the data of the connection
 * 
 * This makes as much as possible use of the official Android Bluetooth communication example:
 * http://web.mit.edu/~mkgray/afs/bar/afs/sipb.mit.edu/project/android/sdk/android-sdk-linux/samples/android-17/BluetoothChat/src/com/example/android/BluetoothChat/BluetoothChatService.java
 *
 */
public class BluetoothConnector {
    // Debugging
    private static final String TAG = BluetoothConnector.class.getSimpleName();
    private static final boolean D = BuildConfig.DEBUG;

    private static final UUID STANDARD_UUID =  UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ServerThread mServerThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothConnector(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothService.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }
    
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mServerThread != null) {mServerThread.cancel(); mServerThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mServerThread = new ServerThread(device);
        mServerThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice  device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mServerThread != null) {mServerThread.cancel(); mServerThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothService.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothService.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mServerThread != null) {
            mServerThread.cancel();
            mServerThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }



    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothService.MESSAGE_CONNECTION_FAILED);
        mHandler.sendMessage(msg);

    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothService.MESSAGE_CONNECTION_LOST);
        mHandler.sendMessage(msg);

    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs and waits for an incoming signal
     */
    private class ServerThread extends Thread {
    	private final BluetoothServerSocket mmServerSocket;
        private BluetoothSocket connectionSocket;
        private final BluetoothDevice mmDevice;

        public ServerThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothServerSocket tmp = null;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
            		tmp = mAdapter.listenUsingRfcommWithServiceRecord("Server", STANDARD_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: create() failed", e);
            }
            mmServerSocket = tmp;
        }
        
        

        public void run() {
        	if (D) Log.i(TAG, "BEGIN mServerThread");
            setName("ServerThread");

            while(mmServerSocket == null){
            	Log.i(TAG, "server socket still null");
            }

            while (true) {
            	try {

            		// Make a connection to the BluetoothSocket           
            		// This is a blocking call and will only return when someone tries to connect
            		connectionSocket = mmServerSocket.accept();
    
            	} catch (IOException e) {
            		try {
            			mmServerSocket.close();
            		} catch (IOException e2) {
            			Log.e(TAG, "unable to close() socket during connection failure", e2);
            		}
            		connectionFailed();
            		return;
            	}
            	if (connectionSocket != null) {
            		try {
            			//it's a good idea to call close() on the BluetoothServerSocket when it's no longer needed for accepting connections.
            			//Closing the BluetoothServerSocket will not close the returned BluetoothSocket.
            			mmServerSocket.close();
            		} catch (IOException e) {
            			Log.e(TAG, "unable to close() socket during connection failure", e);
            		}
            		break;
            	}
            }


            // Reset the ConnectThread because we're done
            synchronized (BluetoothConnector.this) {
                mServerThread = null;
            }
            
            // Start the connected thread
            connected(connectionSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect  socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket) {
        	if (D) Log.d(TAG, "create ConnectedThread ");
            mmSocket = socket;
            InputStream tmpIn = null;

            // Get the BluetoothSocket input stream
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp socket not created", e);
            }

            mmInStream = tmpIn;
        }

        public void run() {
        	if (D) Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(BluetoothService.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                	//this happens regularly connecting to the loneworker bluetooth example device
                	//the disconnection will be reported and restarted
                    //if(D)Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }


        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            	if(D){
            		Log.e(TAG, "close() of connect socket failed", e);
            	}
            }
        }
    }
}