package com.bwssystems.HABridge.dao;


import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bwssystems.HABridge.dao.DeviceDescriptor;
import com.bwssystems.util.BackupHandler;
import com.bwssystems.util.JsonTransformer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
/*
 * This is an in memory list to manage the configured devices and saves the list as a JSON string to a file for later  
 * loading.
 */
public class DeviceRepository extends BackupHandler {
	private Map<String, DeviceDescriptor> devices;
    private Path repositoryPath;
	private Gson gson;
    private Integer nextId;
    private Logger log = LoggerFactory.getLogger(DeviceRepository.class);
	
    public DeviceRepository(String deviceDb) {
		super();
		gson =
                new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
		repositoryPath = null;
		repositoryPath = Paths.get(deviceDb);
		setupParams(repositoryPath, ".bk", "device.db-");
		nextId = 0;
		_loadRepository(repositoryPath);
	}
    
    public void loadRepository() {
    	if(repositoryPath != null)
    		_loadRepository(repositoryPath);
    }
	private void _loadRepository(Path aPath){
		String jsonContent = repositoryReader(aPath);
		devices = new HashMap<String, DeviceDescriptor>();
		
		if(jsonContent != null)
		{
			DeviceDescriptor list[] = gson.fromJson(jsonContent, DeviceDescriptor[].class);
			for(int i = 0; i < list.length; i++) {
				put(list[i].getId(), list[i]);
				if(Integer.decode(list[i].getId()) > nextId) {
					nextId = Integer.decode(list[i].getId());
				}
			}
		}    	
    }
    
	public List<DeviceDescriptor> findAll() {
		List<DeviceDescriptor> list = new ArrayList<DeviceDescriptor>(devices.values());
		return list;
	}
/*
	public List<DeviceDescriptor> findAllByRequester(String anAddress) {
		List<DeviceDescriptor> list = new ArrayList<DeviceDescriptor>(devices.values());
		List<DeviceDescriptor> theReturnList = new ArrayList<DeviceDescriptor>();
		Iterator<DeviceDescriptor> anIterator = list.iterator();
		DeviceDescriptor theDevice;
		String theRequesterAddress;
		while(anIterator.hasNext()) {
			 theDevice = anIterator.next();
			 theRequesterAddress = theDevice.getRequesterAddress();
			 if(theRequesterAddress == null || theRequesterAddress.length() == 0 || theRequesterAddress.contains(anAddress))
				theReturnList.add(theDevice);
		}
		return theReturnList;
	}
*/
	public List<DeviceDescriptor> findAllByRequester(String anAddress) {
		List<DeviceDescriptor> list = new ArrayList<DeviceDescriptor>(devices.values());
		List<DeviceDescriptor> theReturnList = new ArrayList<DeviceDescriptor>();
		Iterator<DeviceDescriptor> anIterator = list.iterator();
		DeviceDescriptor theDevice;
		String theRequesterAddress;

		HashMap<String,String > addressMap;
		while (anIterator.hasNext()) {
			theDevice = anIterator.next();
			theRequesterAddress = theDevice.getRequesterAddress();
			addressMap = new HashMap<String, String>();
			if (theRequesterAddress.contains(",")) {
				String[] theArray = theRequesterAddress.split(",");
				for (String v : theArray) {
					addressMap.put(v, v);
				}
			} else
				addressMap.put(theRequesterAddress, theRequesterAddress);
			if (theRequesterAddress == null || theRequesterAddress.length() == 0 || addressMap.containsKey(anAddress))
				theReturnList.add(theDevice);
		}
		return theReturnList;
	}
	public DeviceDescriptor findOne(String id) {
    	return devices.get(id);	
    }
    
	private void put(String id, DeviceDescriptor aDescriptor) {
        devices.put(id, aDescriptor);
    }
    
	public void save(DeviceDescriptor[] descriptors) {
		String theNames = "";
		for(int i = 0; i < descriptors.length; i++) {
	        if(descriptors[i].getId() != null && descriptors[i].getId().length() > 0)
	        	devices.remove(descriptors[i].getId());
	        else {
	        	nextId++;
	        	descriptors[i].setId(String.valueOf(nextId));
	        }
	        if(descriptors[i].getUniqueid() == null || descriptors[i].getUniqueid().length() == 0) {
	        	BigInteger bigInt = BigInteger.valueOf(Integer.decode(descriptors[i].getId()));
	        	byte[] theBytes = bigInt.toByteArray();
	        	String hexValue = DatatypeConverter.printHexBinary(theBytes);

	        	descriptors[i].setUniqueid("00:17:88:5E:D3:" + hexValue + "-" + hexValue);
	        }
	        put(descriptors[i].getId(), descriptors[i]);
	        theNames = theNames + " " + descriptors[i].getName() + ", ";
		}
    	String  jsonValue = gson.toJson(findAll());
        repositoryWriter(jsonValue, repositoryPath);
        log.debug("Save device(s): " + theNames);
    }
    
	public void renumber() {
		List<DeviceDescriptor> list = new ArrayList<DeviceDescriptor>(devices.values());
		Iterator<DeviceDescriptor> deviceIterator = list.iterator();
		Map<String, DeviceDescriptor> newdevices = new HashMap<String, DeviceDescriptor>();;
		nextId = 0;
        log.debug("Renumber devices.");
		while(deviceIterator.hasNext()) {
        	nextId++;
			DeviceDescriptor theDevice = deviceIterator.next();
        	theDevice.setId(String.valueOf(nextId));
        	BigInteger bigInt = BigInteger.valueOf(nextId);
        	byte[] theBytes = bigInt.toByteArray();
        	String hexValue = DatatypeConverter.printHexBinary(theBytes);
       	
        	theDevice.setUniqueid("00:17:88:5E:D3:" + hexValue + "-" + hexValue);
	        newdevices.put(theDevice.getId(), theDevice);
		}
        devices = newdevices;
    	String  jsonValue = gson.toJson(findAll());
        repositoryWriter(jsonValue, repositoryPath);
    }
    
	public String delete(DeviceDescriptor aDescriptor) {
        if (aDescriptor != null) {
        	devices.remove(aDescriptor.getId());
        	JsonTransformer aRenderer = new JsonTransformer();
        	String  jsonValue = aRenderer.render(findAll());
            repositoryWriter(jsonValue, repositoryPath);
            return "Device with id '" + aDescriptor.getId() + "' deleted";
        } else {
            return "Device not found";
        }

    }
	
	private void repositoryWriter(String content, Path filePath) {
		if(Files.exists(filePath) && !Files.isWritable(filePath)){
			log.error("Error file is not writable: " + filePath);
			return;
		}
		
		if(Files.notExists(filePath.getParent())) {
			try {
				Files.createDirectories(filePath.getParent());
			} catch (IOException e) {
				log.error("Error creating the directory: " + filePath + " message: " + e.getMessage(), e);
			}
		}

		try {
			Path target = null;
			if(Files.exists(filePath)) {
				target = FileSystems.getDefault().getPath(filePath.getParent().toString(), "device.db.old");
				Files.move(filePath, target);
			}
			Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE);
			if(target != null)
				Files.delete(target);
		} catch (IOException e) {
			log.error("Error writing the file: " + filePath + " message: " + e.getMessage(), e);
		}
	}
	
	private String repositoryReader(Path filePath) {

		String content = null;
		if(Files.notExists(filePath) || !Files.isReadable(filePath)){
			log.warn("Error reading the file: " + filePath + " - Does not exist or is not readable. continuing...");
			return null;
		}

		
		try {
			content = new String(Files.readAllBytes(filePath));
		} catch (IOException e) {
			log.error("Error reading the file: " + filePath + " message: " + e.getMessage(), e);
		}
		
		return content;
	}
}