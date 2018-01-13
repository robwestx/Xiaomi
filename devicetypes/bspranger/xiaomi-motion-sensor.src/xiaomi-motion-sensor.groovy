/**
 *  Xiaomi Motion Sensor
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Based on original DH by Eric Maycock 2015
 * modified 29/12/2016 a4refillpad 
 * Added fingerprinting
 * Added heartbeat/lastcheckin for monitoring
 * Added battery and refresh 
 * Motion background colours consistent with latest DH
 * Fixed max battery percentage to be 100%
 * Added Last update to main tile
 * Added last motion tile
 * Heartdeat icon plus improved localisation of date
 * removed non working tiles and changed layout and incorporated latest colours
 * added experimental health check as worked out by rolled54.Why
 *  bspranger - renamed to bspranger to remove confusion of a4refillpad
 *
 */

metadata {
	definition (name: "Xiaomi Motion Sensor", namespace: "bspranger", author: "bspranger") {
		capability "Motion Sensor"
		capability "Configuration"
		capability "Battery"
		capability "Sensor"
		capability "Refresh"
        capability "Health Check" 
        
        attribute "lastCheckin", "String"
        attribute "lastMotion", "String"
	attribute "batteryRuntime", "String"	

    	fingerprint profileId: "0104", deviceId: "0104", inClusters: "0000, 0003, FFFF, 0019", outClusters: "0000, 0004, 0003, 0006, 0008, 0005, 0019", manufacturer: "LUMI", model: "lumi.sensor_motion", deviceJoinName: "Xiaomi Motion"
        
	command "resetBatteryRuntime"	
        command "reset"
        command "Refresh"
        
	}

	simulator {
	}

	preferences {
		input "motionReset", "number", title: "Number of seconds after the last reported activity to report that motion is inactive (in seconds). \n\n(The device will always remain blind to motion for 60seconds following first detected motion. This value just clears the 'active' status after the number of seconds you set here but the device will still remain blind for 60seconds in normal operation.)", description: "", value:120, displayDuringSetup: false
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
			}
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
    			attributeState("default", label:'Last Update: ${currentValue}',icon: "st.Health & Wellness.health9")
            }
		}
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"",
			backgroundColors:[
				[value: 0, color: "#c0392b"],
				[value: 25, color: "#f1c40f"],
				[value: 50, color: "#e67e22"],
				[value: 75, color: "#27ae60"]
			]
		}
              
	    standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("configure", "device.configure", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
	    }       
        
		standardTile("reset", "device.reset", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
			state "default", action:"reset", label: "Reset Motion"
		}
		standardTile("icon", "device.refresh", inactiveLabel: false, decoration: "flat", width: 4, height: 1) {
            state "default", label:'Last Motion:', icon:"st.Entertainment.entertainment15"
        }
        valueTile("lastmotion", "device.lastMotion", decoration: "flat", inactiveLabel: false, width: 4, height: 1) {
			state "default", label:'${currentValue}'
        }
        standardTile("refresh", "command.refresh", inactiveLabel: false) {
			state "default", label:'refresh', action:"refresh.refresh", icon:"st.secondary.refresh-icon"
	   }
		standardTile("batteryRuntime", "device.batteryRuntime", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
			state "batteryRuntime", label:'Battery Changed: ${currentValue} - Tap to reset Date', unit:"", action:"resetBatteryRuntime"
		} 
		main(["motion"])
		details(["motion", "battery", "icon", "lastmotion", "reset", "refresh","batteryRuntime"])
	}
}

def parse(String description) {
    log.debug "${device.displayName} Parsing: $description"

	Map map = [:]
	if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
 
	log.debug "${device.displayName} Parse returned: $map"
	def result = map ? createEvent(map) : null
//  send event for heartbeat    
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    sendEvent(name: "lastCheckin", value: now)
    
    if (description?.startsWith('enroll request')) {
    	List cmds = enrollResponse()
        log.debug "${device.displayName} enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }

       return result
}

private Map getBatteryResult(rawValue) {
    def rawVolts = rawValue / 1000

    def minVolts = 2.7
    def maxVolts = 3.0
    def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
    def roundedPct = Math.min(100, Math.round(pct * 100))

    def result = [
        name: 'battery',
        value: roundedPct,
        unit: "%",
        isStateChange:true,
        descriptionText : "${device.displayName} raw battery is ${rawVolts}v"
    ]
    
    log.debug "${device.displayName}: ${result}"
    
    return result
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
    def i
	log.debug cluster
	if (shouldProcessMessage(cluster)) {
		switch(cluster.clusterId) {
			case 0x0000:
            	def MsgLength = cluster.data.size();
                for (i = 0; i < (MsgLength-3); i++)
                {
                    if ((cluster.data.get(i) == 0x01) && (cluster.data.get(i+1) == 0x21))  // check the data ID and data type
                    {
                        // next two bytes are the battery voltage.
                        resultMap = getBatteryResult((cluster.data.get(i+3)<<8) + cluster.data.get(i+2))
                    }
                }
            	break

			case 0x0402:
				log.debug '${device.displayName}: TEMP'
				// temp is last 2 data values. reverse to swap endian
				String temp = cluster.data[-2..-1].reverse().collect { cluster.hex1(it) }.join()
				def value = getTemperature(temp)
				resultMap = getTemperatureResult(value)
				break
		}
	}

	return resultMap
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	// 0x07 is bind message
	boolean ignoredMessage = cluster.profileId != 0x0104 ||
	cluster.command == 0x0B ||
	cluster.command == 0x07 ||
	(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return !ignoredMessage
}


def configure() {
	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
	log.debug "${device.displayName}: ${device.deviceNetworkId}"
    def endpointId = 1
    log.debug "${device.displayName}: ${device.zigbeeId}"
    log.debug "${device.displayName}: ${zigbeeEui}"
	def configCmds = [
			//battery reporting and heartbeat
			"zdo bind 0x${device.deviceNetworkId} 1 ${endpointId} 1 {${device.zigbeeId}} {}", "delay 200",
			"zcl global send-me-a-report 1 0x20 0x20 600 3600 {01}", "delay 200",
			"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1500",


			// Writes CIE attribute on end device to direct reports to the hub's EUID
			"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
			"send 0x${device.deviceNetworkId} 1 1", "delay 500",
	]

	log.debug "${device.displayName} configure: Write IAS CIE"
	return configCmds
}

def enrollResponse() {
    log.debug "${device.displayName}: Enrolling device into the IAS Zone"
	[
			// Enrolling device into the IAS Zone
			"raw 0x500 {01 23 00 00 00}", "delay 200",
			"send 0x${device.deviceNetworkId} 1 1"
	]
}

def refresh() {
    log.debug "${device.displayName}: Refreshing Battery"
//    def endpointId = 0x01
//	[
//	    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0000 0x0000", "delay 200"
//	    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0000", "delay 200"
//	] //+ enrollResponse()

    zigbee.configureReporting(0x0001, 0x0021, 0x20, 300, 600, 0x01)
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	//log.debug "Desc Map: $descMap"
 
	Map resultMap = [:]
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
   
	if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
	}
    else if (descMap.cluster == "0406" && descMap.attrId == "0000") {
    	def value = descMap.value.endsWith("01") ? "active" : "inactive"
	    sendEvent(name: "lastMotion", value: now)
        if (settings.motionReset == null || settings.motionReset == "" ) settings.motionReset = 120
        if (value == "active") runIn(settings.motionReset, stopMotion)
    	resultMap = getMotionResult(value)
    } 
    else if (descMap.cluster == "0000" && descMap.attrId == "0005") 
    {
        def result = [
			name: 'Model',
			value: ''
		]
        for (int i = 0; i < descMap.value.length(); i+=2) 
        {
            def str = descMap.value.substring(i, i+2);
            def NextChar = (char)Integer.parseInt(str, 16);
            result.value = result.value + NextChar
        }
        resultMap = result
    }
	return resultMap
}
 
private Map parseCustomMessage(String description) {
	Map resultMap = [:]
	return resultMap
}

private Map parseIasMessage(String description) {
    List parsedMsg = description.split(' ')
    String msgCode = parsedMsg[2]
    
    Map resultMap = [:]
    switch(msgCode) {
        case '0x0020': // Closed/No Motion/Dry
        	resultMap = getMotionResult('inactive')
            break

        case '0x0021': // Open/Motion/Wet
        	resultMap = getMotionResult('active')
            break

        case '0x0022': // Tamper Alarm
        	log.debug '${device.displayName}: motion with tamper alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0023': // Battery Alarm
            break

        case '0x0024': // Supervision Report
        	log.debug '${device.displayName}: no motion with tamper alarm'
        	resultMap = getMotionResult('inactive')
            break

        case '0x0025': // Restore Report
            break

        case '0x0026': // Trouble/Failure
        	log.debug '${device.displayName}: motion with failure alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0028': // Test Mode
            break
    }
    return resultMap
}


private Map getMotionResult(value) {
    //log.debug "${device.displayName}: motion"
	String descriptionText = value == 'active' ? "${device.displayName} detected motion" : "${device.displayName} motion has stopped"
	def commands = [
		name: 'motion',
		value: value,
		descriptionText: descriptionText
	] 
    return commands
}

private byte[] reverseArray(byte[] array) {
    byte tmp;
    tmp = array[1];
    array[1] = array[0];
    array[0] = tmp;
    return array
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

def stopMotion() {
   sendEvent(name:"motion", value:"inactive")
}

def reset() {
	sendEvent(name:"motion", value:"inactive")
}

def resetBatteryRuntime() {
    def now = new Date().format("EEE dd MMM yyyy h:mm:ss a", location.timeZone)
    sendEvent(name: "batteryRuntime", value: now)
}

def installed() {
// Device wakes up every 1 hour, this interval allows us to miss one wakeup notification before marking offline
    log.debug "${device.displayName}: Configured health checkInterval when installed()"
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}

def updated() {
// Device wakes up every 1 hours, this interval allows us to miss one wakeup notification before marking offline
    log.debug "${device.displayName}: Configured health checkInterval when updated()"
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}
