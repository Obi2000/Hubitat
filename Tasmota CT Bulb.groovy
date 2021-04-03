//Tasmota HttpHook Driver for Color Temperature Light
//By Obi2000
//

//
//1.0.1


metadata {
	definition(name: "Tasmota CT Bulb", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
        capability "ColorTemperature"
		capability "Light"
		capability "SwitchLevel"
//        capability "ColorMode"
        capability "Refresh"
		
		capability "Change Level"
    }

	preferences {		
		section("Switch Info") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
			input(name: "port", type: "number", title: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
		}
	}
}


def parse(String description) {

	
	def tasdata = [:]
	def msg = parseLanMessage(description)
	if(msg.headers.containsKey("POST / HTTP/1.1")){
		def slurper = new groovy.json.JsonSlurper()
		try {
			tasdata = slurper.parseText(msg.body)
		} catch (e) {
		}
		}



	if (tasdata.containsKey("POWER")) {
        sendEvent(name:"switch", value:"${tasdata.POWER.toLowerCase()}", descriptionText:"Power $tasdata.POWER")
    }  

	if (tasdata.containsKey("CT")) {
		//Color Temperature
		//sendEvent(name: "colorMode", value: "CT")
		kelvin=Math.round(1000000/tasdata.CT)

		sendEvent(name: "colorTemperature", value: kelvin)
		setCTColorName(kelvin)
	}
   

	
	if (tasdata.containsKey("Dimmer")) {
		sendEvent(name:"level", value:"$tasdata.Dimmer", descriptionText:"Dimmer $tasdata.Dimmer")
	}
	

}


def on() {
	sendCommand("Power1", "On")
	
}


def off() {
	sendCommand("Power1", "Off")
}



def setLevel(v, duration=0)
{
	//sendEvent(name: "level", value: "${v}")
    sendCommand("Dimmer", "${v}")
}

private String convertIPtoHex(ipAddress) { 
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format('%04x', port.toInteger())
	return hexport
}

def installed() {

}



def updated() {
	state.dni=convertIPtoHex(ipAddress)
	device.deviceNetworkId = state.dni

	refresh()
}

def refresh(){
	sendCommand("state")
}



def configure() {

}



private def sendCommand(String command){
	sendCommand(command,null)
}


private def sendCommand(String command, payload) {
if(payload!=null){payload = payload.toString()}
//log.debug "cmd:"+command+"pay:"+payload
	if (!ipAddress || !port) {
		log.warn "aborting. ip address or port of device not set"
		return null;
	}
	def hosthex = convertIPtoHex(ipAddress)
	def porthex = convertPortToHex(port)

	def path = "/cm"
	if (payload){
		path += "?cmnd=${command}%20${payload}"
	}
	else{
		path += "?cmnd=${command}"
	}

	if (username){
		path += "&user=${username}"
		if (password){
			path += "&password=${password}"
		}
	}

//log.debug path    
	def result = new hubitat.device.HubAction(
		method: "GET",
		path: path,
		headers: [
			HOST: "${ipAddress}:${port}"
		]
	)

	sendHubCommand(result)
}



def setColorTemperature(CT){
	mired = Math.round(1000000/CT)
    if(mired<153){mired=153}
    if(mired>500){mired=500}
	sendCommand("CT", mired)
	
	if(level!=null){
		setLevel(level,dur)
	}
}


def setColorTemperature(CT,level,dur=null){
	cmd="Backlog"
	
	mired = Math.round(1000000/CT)
    if(mired<153){mired=153}
    if(mired>500){mired=500}

	cmd += "%20CT%20${mired}"  //CT mired
	
	if(level!=null){
		cmd += "%3BDimmer%20${level}"  //Dimmer level
	}
	
	sendCommand(cmd)
}



def setCTColorName(value)
{
		if (value <= 2000) {
			sendEvent(name: "colorName", value: "Candlelight")
		}
		if (value < 2600) {
			sendEvent(name: "colorName", value: "Warm White")
		}
		else if (value < 3500) {
			sendEvent(name: "colorName", value: "Soft White")
		}
		else if (value < 4500) {
			sendEvent(name: "colorName", value: "White")
		}
		else if (value < 6000) {
			sendEvent(name: "colorName", value: "Daylight")
		}
		else if (value >=  6000) {
			sendEvent(name: "colorName", value: "Cool White")
		}
	
}



//Dimmer >
void startLevelChange(dir){
	if(dir=="up"){setLevel(100)}
	if(dir=="down"){setLevel(0)}
}

//Dimmer !
void stopLevelChange(){

}



