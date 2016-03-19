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
package com.ghgande.j2mod.modbus.cmd;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.ModbusIOException;
import com.ghgande.j2mod.modbus.ModbusSlaveException;
import com.ghgande.j2mod.modbus.io.ModbusSerialTransport;
import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.io.ModbusTransport;
import com.ghgande.j2mod.modbus.msg.ExceptionResponse;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.msg.WriteFileRecordRequest;
import com.ghgande.j2mod.modbus.msg.WriteFileRecordRequest.RecordRequest;
import com.ghgande.j2mod.modbus.msg.WriteFileRecordResponse;
import com.ghgande.j2mod.modbus.msg.WriteFileRecordResponse.RecordResponse;
import com.ghgande.j2mod.modbus.net.ModbusMasterFactory;
import com.ghgande.j2mod.modbus.util.Logger;

import java.io.IOException;
import java.util.Arrays;

/**
 * ReadFileRecordText -- Exercise the "READ FILE RECORD" Modbus
 * message.
 *
 * @author Julie
 * @version 0.96
 */
public class WriteFileRecordTest {

    private static final Logger logger = Logger.getLogger(WriteFileRecordTest.class);

    /**
     * usage -- Print command line arguments and exit.
     */
    private static void usage() {
        logger.debug("Usage: WriteFileRecordTest connection unit file record value [value ...]");

        System.exit(1);
    }

    public static void main(String[] args) {
        ModbusTransport transport = null;
        WriteFileRecordRequest request;
        WriteFileRecordResponse response;
        ModbusTransaction trans;
        int unit = 0;
        int file = 0;
        int record = 0;
        int registers;
        short values[] = null;
        boolean isSerial = false;

		/*
         * Get the command line parameters.
		 */
        if (args.length < 6) {
            usage();
        }

        try {
            transport = ModbusMasterFactory.createModbusMaster(args[0]);
            if (transport instanceof ModbusSerialTransport) {
                ((ModbusSerialTransport)transport).setReceiveTimeout(500);
                ((ModbusSerialTransport)transport).setBaudRate(19200);
                isSerial = true;

                Thread.sleep(2000);
            }
            unit = Integer.parseInt(args[1]);
            file = Integer.parseInt(args[2]);
            record = Integer.parseInt(args[3]);

            if (args.length > 4) {
                registers = args.length - 4;
                values = new short[registers];

                for (int i = 0; i < registers; i++) {
                    values[i] = Short.parseShort(args[i + 4]);
                }
            }
        }
        catch (NumberFormatException x) {
            System.err.println("Invalid parameter");
            usage();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            usage();
            System.exit(1);
        }

        try {
            /*
             * Setup the WRITE FILE RECORD request.
			 */
            request = new WriteFileRecordRequest();
            request.setUnitID(unit);
            if (isSerial) {
                request.setHeadless(true);
            }

            RecordRequest recordRequest = new RecordRequest(file, record, values);
            request.addRequest(recordRequest);

            if (Modbus.debug) {
                logger.debug("Request: " + request.getHexMessage());
            }

			/*
			 * Setup the transaction.
			 */
            trans = transport.createTransaction();
            trans.setRequest(request);

			/*
			 * Execute the transaction.
			 */
            try {
                trans.execute();
            }
            catch (ModbusSlaveException x) {
                System.err.println("Slave Exception: " + x.getLocalizedMessage());
                System.exit(1);
            }
            catch (ModbusIOException x) {
                System.err.println("I/O Exception: " + x.getLocalizedMessage());
                System.exit(1);
            }
            catch (ModbusException x) {
                System.err.println("Modbus Exception: " + x.getLocalizedMessage());
                System.exit(1);
            }

            ModbusResponse dummy = trans.getResponse();
            if (dummy == null) {
                System.err.println("No response for transaction ");
                System.exit(1);
            }
            if (dummy instanceof ExceptionResponse) {
                ExceptionResponse exception = (ExceptionResponse)dummy;

                System.err.println(exception);
            }
            else if (dummy instanceof WriteFileRecordResponse) {
                response = (WriteFileRecordResponse)dummy;

                if (Modbus.debug) {
                    logger.debug("Response: " + response.getHexMessage());
                }

                int count = response.getRequestCount();
                for (int j = 0; j < count; j++) {
                    RecordResponse data = response.getRecord(j);
                    values = new short[data.getWordCount()];
                    for (int k = 0; k < data.getWordCount(); k++) {
                        values[k] = data.getRegister(k).toShort();
                    }

                    logger.debug("data[" + j + "] = " + Arrays.toString(values));
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            if (transport != null) {
                try {
                    transport.close();
                }
                catch (IOException e) {
                    // Do nothing.
                }
            }
        }
        System.exit(0);
    }
}
