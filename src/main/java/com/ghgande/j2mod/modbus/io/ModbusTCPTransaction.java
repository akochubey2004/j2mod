/*
 * This file is part of j2mod-steve.
 *
 * j2mod is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * j2mod is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses
 */
package com.ghgande.j2mod.modbus.io;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.ModbusIOException;
import com.ghgande.j2mod.modbus.ModbusSlaveException;
import com.ghgande.j2mod.modbus.msg.ExceptionResponse;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;

/**
 * Class implementing the <tt>ModbusTransaction</tt> interface.
 *
 * @author Dieter Wimberger
 * @version 021212- jfhaugh (jfh@ghgande.com) Added code to re-read a response
 *          if the transaction IDs have gotten out of sync.
 */
public class ModbusTCPTransaction implements ModbusTransaction {

    // class attributes
    private static int c_TransactionID = Modbus.DEFAULT_TRANSACTION_ID;

    // instance attributes and associations
    private TCPMasterConnection m_Connection;
    private ModbusTransport m_IO;
    private ModbusRequest m_Request;
    private ModbusResponse m_Response;
    private boolean m_ValidityCheck = Modbus.DEFAULT_VALIDITYCHECK;
    private boolean m_Reconnecting = Modbus.DEFAULT_RECONNECTING;
    private int m_Retries = Modbus.DEFAULT_RETRIES;

    /**
     * Constructs a new <tt>ModbusTCPTransaction</tt> instance.
     */
    public ModbusTCPTransaction() {
    }

    /**
     * Constructs a new <tt>ModbusTCPTransaction</tt> instance with a given
     * <tt>ModbusRequest</tt> to be send when the transaction is executed.
     * <p>
     *
     * @param request a <tt>ModbusRequest</tt> instance.
     */
    public ModbusTCPTransaction(ModbusRequest request) {
        setRequest(request);
    }

    /**
     * Constructs a new <tt>ModbusTCPTransaction</tt> instance with a given
     * <tt>TCPMasterConnection</tt> to be used for transactions.
     * <p>
     *
     * @param con a <tt>TCPMasterConnection</tt> instance.
     */
    public ModbusTCPTransaction(TCPMasterConnection con) {
        setConnection(con);
        m_IO = con.getModbusTransport();
    }

    /**
     * Sets the connection on which this <tt>ModbusTransaction</tt> should be
     * executed.
     * <p>
     * An implementation should be able to handle open and closed connections.
     * <br>
     * <p>
     *
     * @param con a <tt>TCPMasterConnection</tt>.
     */
    public void setConnection(TCPMasterConnection con) {
        m_Connection = con;
        m_IO = con.getModbusTransport();
    }

    /**
     * Tests if the connection will be opened and closed for <b>each</b>
     * execution.
     * <p>
     *
     * @return true if reconnecting, false otherwise.
     */
    public boolean isReconnecting() {
        return m_Reconnecting;
    }    public void setRequest(ModbusRequest req) {
        m_Request = req;
    }

    /**
     * Sets the flag that controls whether a connection is opened and closed
     * for <b>each</b> execution or not.
     * <p>
     *
     * @param b true if reconnecting, false otherwise.
     */
    public void setReconnecting(boolean b) {
        m_Reconnecting = b;
    }    public ModbusRequest getRequest() {
        return m_Request;
    }

    /**
     * checkValidity -- Verify the transaction IDs match or are zero.
     *
     * @throws ModbusException if the transaction was not valid.
     */
    private void checkValidity() throws ModbusException {
        if (m_Request.getTransactionID() == 0
                || m_Response.getTransactionID() == 0) {
            return;
        }

        if (m_Request.getTransactionID() != m_Response.getTransactionID()) {
            throw new ModbusException("Transaction ID mismatch");
        }
    }    public ModbusResponse getResponse() {
        return m_Response;
    }

    /**
     * incrementTransactionID -- Increment the transaction ID for the next
     * transaction. Note that the caller must get the new transaction ID with
     * getTransactionID(). This is only done validity checking is enabled so
     * that dumb slaves don't cause problems. The original request will have its
     * transaction ID incremented as well so that sending the same transaction
     * again won't cause problems.
     */
    private void incrementTransactionID() {
        if (isCheckingValidity()) {
            if (c_TransactionID >= Modbus.MAX_TRANSACTION_ID) {
                c_TransactionID = 1;
            }
            else {
                c_TransactionID++;
            }
        }
        m_Request.setTransactionID(getTransactionID());
    }    /**
     * getTransactionID -- get the next transaction ID to use.
     *
     * Note that this method is not synchronized. Callers should synchronize
     * on this class instance if multiple threads can create requests at the
     * same time.
     */
    public int getTransactionID() {
        /*
         * Ensure that the transaction ID is in the valid range between
		 * 1 and MAX_TRANSACTION_ID (65534).  If not, the value will be forced
		 * to 1.
		 */
        if (c_TransactionID <= 0 && isCheckingValidity()) {
            c_TransactionID = 1;
        }

        if (c_TransactionID >= Modbus.MAX_TRANSACTION_ID) {
            c_TransactionID = 1;
        }

        return c_TransactionID;
    }

    public void setCheckingValidity(boolean b) {
        m_ValidityCheck = b;
    }

    public boolean isCheckingValidity() {
        return m_ValidityCheck;
    }





    public int getRetries() {
        return m_Retries;
    }

    public void setRetries(int num) {
        m_Retries = num;
    }

    public void execute() throws ModbusIOException, ModbusSlaveException, ModbusException {

        if (m_Request == null || m_Connection == null) {
            throw new ModbusException("Invalid request or connection");
        }

		/*
         * Automatically re-connect if disconnected.
		 */
        if (!m_Connection.isConnected()) {
            try {
                m_Connection.connect();
            }
            catch (Exception ex) {
                throw new ModbusIOException("Connection failed.");
            }
        }

		/*
		 * Try sending the message up to m_Retries time. Note that the message
		 * is read immediately after being written, with no flushing of buffers.
		 */
        int retryCounter = 0;
        int retryLimit = (m_Retries > 0 ? m_Retries : 1);

        while (retryCounter < retryLimit) {
            try {
                synchronized (m_IO) {
                    if (Modbus.debug) {
                        System.err.println("request transaction ID = " + m_Request.getTransactionID());
                    }

                    m_IO.writeMessage(m_Request);
                    m_Response = null;
                    do {
                        m_Response = m_IO.readResponse();
                        if (Modbus.debug) {
                            System.err.println("response transaction ID = " + m_Response.getTransactionID());

                            if (m_Response.getTransactionID() != m_Request.getTransactionID()) {
                                System.err.println("expected " + m_Request.getTransactionID() + ", got " + m_Response.getTransactionID());
                            }
                        }
                    } while (m_Response != null
                            && (!isCheckingValidity() ||
                            (m_Request.getTransactionID() != 0 && m_Request.getTransactionID() != m_Response.getTransactionID()))
                            && ++retryCounter < retryLimit);

                    if (retryCounter >= retryLimit) {
                        throw new ModbusIOException("Executing transaction failed (tried " + m_Retries + " times)");
                    }

					/*
					 * Both methods were successful, so the transaction must
					 * have been executed.
					 */
                    break;
                }
            }
            catch (ModbusIOException ex) {
                if (!m_Connection.isConnected()) {
                    try {
                        m_Connection.connect();
                    }
                    catch (Exception e) {
						/*
						 * Nope, fail this transaction.
						 */
                        throw new ModbusIOException("Connection lost.");
                    }
                }
                if (retryCounter >= retryLimit) {
                    throw new ModbusIOException("Executing transaction failed (tried " + m_Retries + " times)");
                }
                else {
                    retryCounter++;
                }
            }
        }

		/*
		 * The slave may have returned an exception -- check for that.
		 */
        if (m_Response instanceof ExceptionResponse) {
            throw new ModbusSlaveException(((ExceptionResponse)m_Response).getExceptionCode());
        }

		/*
		 * Close the connection if it isn't supposed to stick around.
		 */
        if (isReconnecting()) {
            m_Connection.close();
        }

		/*
		 * See if packets require validity checking.
		 */
        if (isCheckingValidity() && m_Request != null && m_Response != null) {
            checkValidity();
        }

        incrementTransactionID();
    }




}
