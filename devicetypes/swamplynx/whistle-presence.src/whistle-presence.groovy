/*
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

preferences {
        input "token", "text", title: "Whistle Token", description: "Whistle Login Token", required: true
        input "petID", "number", title: "Whistle Pet ID", description: "Whistle Pet ID #", required: true
        input "homeID", "number", title: "Whistle Home ID", description: "Whistle Home ID #", required: true
        input "refreshRate", "enum", title: "Data Refresh Rate", defaultValue: 0, options:[0: "Never", 10: "Every 10 Minutes", 15: "Every 15 Minutes", 20: "Every 20 Minutes", 30: "Every 30 Minutes", 60: "Every Hour"], displayDuringSetup: true
		}

metadata {
	definition (name: "Whistle Presence", namespace: "swamplynx", author: "SwampLynx") {
		capability "Presence Sensor"
		capability "Occupancy Sensor"
		capability "Sensor"
        capability "Battery"
        capability "Refresh"
        capability "Polling"
	}

	simulator {
		status "present": "presence: 1"
		status "not present": "presence: 0"
		status "occupied": "occupancy: 1"
		status "unoccupied": "occupancy: 0"
	}

	tiles {
		standardTile("presence", "device.presence", width: 2, height: 2, canChangeBackground: true) {
			state("present", labelIcon:"st.presence.tile.mobile-present", backgroundColor:"#00A0DC")
			state("not present", labelIcon:"st.presence.tile.mobile-not-present", backgroundColor:"#ffffff")
		}
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
			state("battery", label:'${currentValue}% battery', unit:"")
		}
        standardTile("refresh", "device.weather", decoration: "flat", width: 1, height: 1) {
            state "default", label: "", action: "refresh", icon:"st.secondary.refresh"
        }
		main (["presence", "battery", "refresh"])
		details (["presence", "battery", "refresh"])
	}
}

def parse(String description) {
	def name = parseName(description)
	def value = parseValue(description)
	def linkText = getLinkText(device)
	def descriptionText = parseDescriptionText(linkText, value, description)
	def handlerName = getState(value)
	def isStateChange = isStateChange(device, name, value)

	def results = [
    	translatable: true,
		name: name,
		value: value,
		unit: null,
		linkText: linkText,
		descriptionText: descriptionText,
		handlerName: handlerName,
		isStateChange: isStateChange,
		displayed: displayed(description, isStateChange)
	]
	log.debug "Parse returned $results.descriptionText"
	return results
}

private String parseName(String description) {
	if (description?.startsWith("presence: ")) {
		return "presence"
	} else if (description?.startsWith("occupancy: ")) {
		return "occupancy"
	}
	null
}

private String parseValue(String description) {
	switch(description) {
		case "presence: 1": return "present"
		case "presence: 0": return "not present"
		case "occupancy: 1": return "occupied"
		case "occupancy: 0": return "unoccupied"
		default: return description
	}
}

private parseDescriptionText(String linkText, String value, String description) {
	switch(value) {
		case "present": return "{{ linkText }} has arrived"
		case "not present": return "{{ linkText }} has left"
		case "occupied": return "{{ linkText }} is inside"
		case "unoccupied": return "{{ linkText }} is away"
		default: return value
	}
}

private getState(String value) {
	switch(value) {
		case "present": return "arrived"
		case "not present": return "left"
		case "occupied": return "inside"
		case "unoccupied": return "away"
		default: return value
	}
}

def refresh() { 
	 log.info("Whistle presence status Refresh Requested")
    callAPI()
}
def poll() { 
	 log.info("Whistle presence status Poll Request or Scheduled Poll")
    callAPI()
}

def getAPIkey() {
	return "Bearer ${token}"
}

private def callAPI() {
    if (petID){
        def refreshTime =  refreshRate ? (refreshRate as int) * 60 : 0
        if (refreshTime > 0) {
            runIn (refreshTime, poll)
            log.debug "Data will repoll every ${refreshRate} minutes"   
        }
        else log.debug "Data will never automatically repoll"   
    
        def accessToken = getAPIkey()
        
        def params = [
            uri: "https://app.whistle.com",
            path: "/api/pets/${petID}",
            contentType: "application/json",
            headers: [
            	"Authorization": "${accessToken}",
                "Accept": "application/vnd.whistle.com.v4+json",
                "Content-Type": "application/json",
                "Connection": "keep-alive",
                "Accept-Language": "en-us",
                "Accept-Encoding": "br, gzip, deflate",
                "User-Agent": "Winston/2.5.3 (iPhone; iOS 12.0.1; Build:1276; Scale/2.0)" ],
                      ]
      try {
      	log.debug "Starting HTTP GET request to Whistle API"
    	httpGet(params) { resp ->
  //  		if (resp.data) {
  //      		log.debug "Response Data = ${resp.data}"
  //      		log.debug "Response Status = ${resp.status}"
  //
  //            resp.headers.each {
  //			log.debug "header: ${it.name}: ${it.value}"
  //				}
  //      	}
        	if(resp.status == 200) {
	        	log.debug "Request to Whistle API was OK, parsing data"
  
                def batt = resp.data.pet.device.battery_level
                log.debug "Updating Whistle battery status to ${batt}%"
                sendEvent(name:"battery", value: batt, unit: "%")
                
                def locationIDnum = resp.data.pet.last_location.place.id.toInteger()
                def locationStatus = resp.data.pet.last_location.place.status.toString()
                def homeIDnum = "${homeID}".toInteger()
                
                log.debug "Current Home ID is ${homeIDnum}"
                log.debug "Current Pet Location ID is ${locationIDnum}"
                log.debug "Current Pet Location Status is ${locationStatus}"
               
                
                if (locationIDnum.equals(homeIDnum) && locationStatus.equals("in_beacon_range")) {
                                sendEvent(name: "presence", value: "present")
                                log.debug "Pet is Home, Updating Presence to Present"
                            } else {
                                sendEvent(name: "presence", value: "not present")
                                log.debug "Pet is NOT Home, Updating Presence to Not Present"
                            }
            
    		}
            
        	else {
        		log.error "Request got HTTP status ${resp.status}"
        	}
        }
    } catch(e)
    {
    	log.debug e
    }
}
       else log.debug "The Pet ID missing from the device settings"
   }
