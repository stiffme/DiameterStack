package com.esipeng.diameter.node;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.logging.Level;
//import java.util.logging.Logger;




import org.slf4j.Logger;

import com.esipeng.diameter.InvalidAVPLengthException;

class TCPNode extends NodeImplementation
{
	private Thread node_thread;
	private Selector selector;
	private ServerSocketChannel serverChannel;
	private boolean please_stop;
	private long shutdown_deadline;

	public TCPNode(Node paramNode, NodeSettings paramNodeSettings, Logger paramLogger)
	{
		super(paramNode, paramNodeSettings, paramLogger);
	}

	void openIO() throws java.io.IOException
	{
		this.selector = Selector.open();
		if (this.settings.port() != 0)
		{
			this.serverChannel = ServerSocketChannel.open();

			java.net.ServerSocket localServerSocket = this.serverChannel.socket();

			localServerSocket.bind(new InetSocketAddress(this.settings.port()));
		}
	}

	void start() {
		this.logger.debug( "Starting TCP node");
		this.please_stop = false;
		this.node_thread = new SelectThread();
		this.node_thread.setDaemon(true);
		this.node_thread.start();
		this.logger.debug( "Started TCP node");
	}

	void wakeup() {
		this.logger.debug( "Waking up selector thread");
		this.selector.wakeup();
	}

	void initiateStop(long paramLong) {
		this.logger.debug( "Initiating stop of TCP node");
		this.please_stop = true;
		this.shutdown_deadline = paramLong;
		this.logger.debug( "Initiated stop of TCP node");
	}

	void join() {
		this.logger.debug( "Joining selector thread");
		try {
			this.node_thread.join();
		} catch (InterruptedException localInterruptedException) {}
		this.node_thread = null;
		this.logger.debug( "Selector thread joined");
	}

	void closeIO() {
		this.logger.debug( "Closing server channel, etc.");
		if (this.serverChannel != null) {
			try {
				this.serverChannel.close();
			} catch (java.io.IOException localIOException1) {}
		}
		this.serverChannel = null;
		try {
			this.selector.close();
		} catch (java.io.IOException localIOException2) {}
		this.selector = null;
		this.logger.debug( "Closed selector, etc.");
	}

	private class SelectThread
	extends Thread {
		public SelectThread() { super(); }

		public void run() {
			try {
				run_();
				if (TCPNode.this.serverChannel != null)
					TCPNode.this.serverChannel.close();
			} catch (java.io.IOException localIOException) {}
			catch (InvalidAVPLengthException e) {
				logger.warn("Exception {}",e);
			}     
		}

		private void run_() throws java.io.IOException, InvalidAVPLengthException { 
			if (TCPNode.this.serverChannel != null)
			{
				TCPNode.this.serverChannel.configureBlocking(false);


				TCPNode.this.serverChannel.register(TCPNode.this.selector, 16);
			}
			for (;;)
			{
				if (TCPNode.this.please_stop) {
					if (System.currentTimeMillis() >= TCPNode.this.shutdown_deadline)
						break;
					if (!TCPNode.this.anyOpenConnections())
						break;
				}
				long l1 = TCPNode.this.calcNextTimeout();

				int i;
				if (l1 != -1L) {
					long l2 = System.currentTimeMillis();
					if (l1 > l2) {
						i = TCPNode.this.selector.select(l1 - l2);
					} else
						i = TCPNode.this.selector.selectNow();
				} else {
					i = TCPNode.this.selector.select();
				}


				Iterator<SelectionKey> selectonKeyIterator = TCPNode.this.selector.selectedKeys().iterator();

				while (selectonKeyIterator.hasNext()) {
					SelectionKey selectionKey = selectonKeyIterator.next();
					//Object localObject1;
					//Object localObject2; 
					if (selectionKey.isAcceptable()) {
						TCPNode.this.logger.debug( "Got an inbound connection (key is acceptable)");
						ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
						SocketChannel clientSocketChannel = serverSocketChannel.accept();
						InetSocketAddress localInetSocketAddress = (InetSocketAddress)clientSocketChannel.socket().getRemoteSocketAddress();
						TCPNode.this.logger.debug( "Got an inbound connection from " + localInetSocketAddress.toString());
						if (!TCPNode.this.please_stop) {
							TCPConnection localTCPConnection = new TCPConnection(TCPNode.this, TCPNode.this.settings.watchdogInterval(), TCPNode.this.settings.idleTimeout());
							localTCPConnection.host_id = localInetSocketAddress.getAddress().getHostAddress();
							localTCPConnection.state = Connection.State.connected_in;
							localTCPConnection.channel = clientSocketChannel;
							clientSocketChannel.configureBlocking(false);
							clientSocketChannel.register(TCPNode.this.selector, 1, localTCPConnection);

							TCPNode.this.registerInboundConnection(localTCPConnection);
						}
						else {
							clientSocketChannel.close();
						}
					} else if (selectionKey.isConnectable()) {
						TCPNode.this.logger.debug( "An outbound connection is ready (key is connectable)");
						SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
						TCPConnection connection = (TCPConnection)selectionKey.attachment();
						try {
							if (socketChannel.finishConnect()) {
								TCPNode.this.logger.debug( "Connected!");
								connection.state = Connection.State.connected_out;
								socketChannel.register(TCPNode.this.selector, 1, connection);
								TCPNode.this.initiateCER(connection);
							}
						} catch (java.io.IOException localIOException1) {
							//TCPNode.this.logger.warn( "Connection to '" + ((TCPConnection)localObject2).host_id + "' failed", localIOException1);
							TCPNode.this.logger.warn( "Connection to '{}' failed, {}" ,connection.host_id ,localIOException1);
							try {
								socketChannel.register(TCPNode.this.selector, 0);
								socketChannel.close();
							} catch (java.io.IOException localIOException2) {}
							TCPNode.this.unregisterConnection(connection);
						}
					} else if (selectionKey.isReadable()) {
						TCPNode.this.logger.debug( "Key is readable");

						SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
						TCPConnection connection = (TCPConnection)selectionKey.attachment();
						TCPNode.this.handleReadable(connection);
						if ((connection.state != Connection.State.closed) && (connection.hasNetOutput()))
						{
							socketChannel.register(TCPNode.this.selector, 5, connection); 
						}
					} else if (selectionKey.isWritable()) {
						TCPNode.this.logger.debug( "Key is writable");
						SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
						TCPConnection connection = (TCPConnection)selectionKey.attachment();
						synchronized (TCPNode.this.getLockObject()) {
							TCPNode.this.handleWritable((Connection)connection);
							if ((connection.state != Connection.State.closed) && (connection.hasNetOutput()))
							{
								socketChannel.register(TCPNode.this.selector, 5, connection);
							}
						}
					}

					selectonKeyIterator.remove();
				}

				TCPNode.this.runTimers();
			}
		}
	}




	private void handleReadable(TCPConnection connection) throws InvalidAVPLengthException
	{
		this.logger.debug( "handlereadable()...");
		connection.makeSpaceInNetInBuffer();
		ConnectionBuffers localConnectionBuffers = connection.connection_buffers;
		this.logger.debug( "pre: conn.in_buffer.position={}" , localConnectionBuffers.netInBuffer().position());
		int i;
		try {
			int j = 0;
			while (((i = connection.channel.read(localConnectionBuffers.netInBuffer())) > 0) && (j++ < 3)) {
				this.logger.debug( "readloop: connection_buffers.netInBuffer().position=" + localConnectionBuffers.netInBuffer().position());
				connection.makeSpaceInNetInBuffer();
			}
		} catch (java.io.IOException localIOException) {
			this.logger.debug( "got IOException", localIOException);
			closeConnection(connection);
			return;
		}
		connection.processNetInBuffer();
		processInBuffer(connection);
		if ((i < 0) && (connection.state != Connection.State.closed)) {
			this.logger.debug( "count<0");
			closeConnection(connection);
			return;
		}
	}

	private void processInBuffer(TCPConnection connection) throws InvalidAVPLengthException {
		java.nio.ByteBuffer localByteBuffer = connection.connection_buffers.appInBuffer();
		this.logger.debug( "pre: app_in_buffer.position=" + localByteBuffer.position());
		int i = localByteBuffer.position();
		byte[] arrayOfByte = new byte[i];
		localByteBuffer.position(0);
		localByteBuffer.get(arrayOfByte);
		localByteBuffer.position(i);
		int j = 0;

		while (j < arrayOfByte.length)
		{
			int k = arrayOfByte.length - j;
			if (k < 4) break;
			int m = com.esipeng.diameter.Message.decodeSize(arrayOfByte, j);
			if (k < m) break;
			com.esipeng.diameter.Message localMessage = new com.esipeng.diameter.Message();
			com.esipeng.diameter.Message.decode_status localdecode_status = localMessage.decode(arrayOfByte, j, m);

			switch (localdecode_status) {
			case decoded: 
				logRawDecodedPacket(arrayOfByte, j, m);
				j += m;
				boolean bool = handleMessage(localMessage, connection);
				if (!bool) {
					this.logger.debug( "handle error");
					closeConnection(connection); return;
				}


				break;
			case not_enough: 
				break;
			case garbage: 
				logGarbagePacket(connection, arrayOfByte, j, m);
				closeConnection(connection, true);
				return;
			}
			if (localdecode_status == com.esipeng.diameter.Message.decode_status.not_enough) break;
		}
		connection.consumeAppInBuffer(j);
	}

	private void handleWritable(Connection connection) {
		TCPConnection tcpConnection = (TCPConnection)connection;
		this.logger.debug( "handleWritable():");
		java.nio.ByteBuffer localByteBuffer = tcpConnection.connection_buffers.netOutBuffer();



		localByteBuffer.flip();

		try
		{
			int i = tcpConnection.channel.write(localByteBuffer);
			if (i < 0) {
				closeConnection(tcpConnection);
				return;
			}

			localByteBuffer.compact();
			tcpConnection.processAppOutBuffer();
			if (!tcpConnection.hasNetOutput())
				tcpConnection.channel.register(this.selector, 1, tcpConnection);
		} catch (java.io.IOException localIOException) {
			closeConnection(tcpConnection);
			return;
		}
	}

	void sendMessage(TCPConnection connection, byte[] paramArrayOfByte) {
		int i = !connection.hasNetOutput() ? 1 : 0;
		connection.makeSpaceInAppOutBuffer(paramArrayOfByte.length);

		connection.connection_buffers.appOutBuffer().put(paramArrayOfByte);
		connection.connection_buffers.processAppOutBuffer();


		if (i != 0)
			outputBecameAvailable(connection);
	}

	private void outputBecameAvailable(Connection connection) {
		TCPConnection tcpConnection = (TCPConnection)connection;
		handleWritable(tcpConnection);
		if (tcpConnection.hasNetOutput()) {
			try {
				tcpConnection.channel.register(this.selector, 5, tcpConnection);
			} catch (java.nio.channels.ClosedChannelException localClosedChannelException) {}
		}
	}

	boolean initiateConnection(Connection connection, Peer peer) {
		TCPConnection tcpConnection = (TCPConnection)connection;
		try {
			SocketChannel socketChannel = SocketChannel.open();
			socketChannel.configureBlocking(false);
			NodeSettings.PortRange portRange = this.settings.TCPPortRange();
			if (portRange != null)
				bindChannelInRange(socketChannel, portRange.min, portRange.max);
			InetSocketAddress localInetSocketAddress;
			if(peer.getRealAddress() != null && peer.getRealAddress().length() > 0)	{
				localInetSocketAddress = new InetSocketAddress(peer.getRealAddress(), peer.port());
			} else	{
				localInetSocketAddress = new InetSocketAddress(peer.host(), peer.port());
			}
			//java.net.InetSocketAddress localInetSocketAddress = new java.net.InetSocketAddress(paramPeer.host(), paramPeer.port());
			try {
				this.logger.debug( "Initiating TCP connection to " + localInetSocketAddress.toString());
				if (socketChannel.connect(localInetSocketAddress))
				{
					this.logger.debug( "Connected!");
					tcpConnection.state = Connection.State.connected_out;
					tcpConnection.channel = socketChannel;
					this.selector.wakeup();
					socketChannel.register(this.selector, 1, tcpConnection);
					initiateCER(tcpConnection);
					return true;
				}
			} catch (java.nio.channels.UnresolvedAddressException localUnresolvedAddressException) {
				socketChannel.close();
				return false;
			} catch (InvalidAVPLengthException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			tcpConnection.state = Connection.State.connecting;
			tcpConnection.channel = socketChannel;
			this.selector.wakeup();
			socketChannel.register(this.selector, 8, tcpConnection);
		} catch (java.io.IOException localIOException) {
			this.logger.warn( "java.io.IOException caught while initiating connection to '" + peer.host() + "'.", localIOException);
		}
		return true;
	}

	void close(Connection connection, boolean linger) {
		TCPConnection localTCPConnection = (TCPConnection)connection;
		try {
			localTCPConnection.channel.register(this.selector, 0);
			if (linger)
			{

				localTCPConnection.channel.socket().setSoLinger(true, 0);
			}
			localTCPConnection.channel.close();
		} catch (java.io.IOException localIOException) {}
	}

	Connection newConnection(long watchDogTimer, long idleCloserTimer) {
		return new TCPConnection(this, watchDogTimer, idleCloserTimer);
	}

	private static int last_tried_port = 0;

	private void bindChannelInRange(SocketChannel socketChannel, int lowPort, int highPort) throws java.io.IOException {
		int i = highPort - lowPort + 1;
		int j = 0; if (j < i) {
			last_tried_port += 1;
			if (last_tried_port < lowPort) last_tried_port = lowPort;
			if (last_tried_port > highPort) last_tried_port = lowPort;
			try {
				socketChannel.socket().bind(new InetSocketAddress(last_tried_port));
			} catch (java.net.BindException localBindException) {}
			return;
		}
		throw new java.net.BindException("Could not bind socket in range {} - {}" + lowPort + "-" + highPort);
	}
}
