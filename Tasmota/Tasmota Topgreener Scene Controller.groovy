
//Tasmota HttpHook Driver for Topgreener Scene Controller
//By Obi2000
//
//Add rules to map tuya response to variable use variable value as button pressed in driver
//Rule1 on TuyaReceived#Data=55AA00070005650400010075 do var1 1 endon
//		on TuyaReceived#Data=55AA00070005660400010076 do var1 2 endon on 
//Etc.
//
//1.0.5


metadata {
	definition(name: "Tasmota Topgreener Scene Controller - TGWFSC8", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
        capability "Refresh"
		capability "PushableButton"
		capability "DoubleTapableButton"
		
    }

	preferences {		
		section("Switch Info") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
			input(name: "port", type: "number", title: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
 //           input(name: "numButtons", type: "number", title: "Number of Buttons", displayDuringSetup: true, required: true, defaultValue: 7)
 //           input(name: "so73", type: "bool", title: "so73 - Decouple Relays/Enable Buttons", displayDuringSetup: true, required: true, defaultValue: true)
 //           input(name: "so32", type: "number", title: "so32 - Time to trigger hold(0.1s), Warning reset due to hold is 10x this value", displayDuringSetup: true)
 //           input(name: "so1", type: "bool", title: "so1 - Disable reset due to hold", displayDuringSetup: true)
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
    
                
	if (tasdata.containsKey("Var1")) {
 
		button = Integer.parseInt(tasdata.Var1)
		if(button<=7){
			action = "SINGLE" 
		}
		else{
			button = button - 7
			action = "DOUBLE" 
		}
 		
		if(action=="SINGLE"){
			sendEvent(name: "pushed", value: button, isStateChange: true )
		}
		
		if(action=="DOUBLE"){
			sendEvent(name: "doubleTapped", value: button, isStateChange: true )
		}
		
        log.info "Button:$button Action:$action"

    }  
    
}


def on() {
    //sendEvent(name: "switch", value: "on")
	sendCommand("Power", "On")
	
}


def off() {
    //sendEvent(name: "switch", value: "off")
	sendCommand("Power", "Off")
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
    sendEvent(name: "numberOfButtons", value: 7)
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

void push(buttonId) { sendEvent(name:"pushed", value: buttonId, isStateChange:true) }

void doubleTap(buttonId) { sendEvent(name:"doubleTapped", value: buttonId, isStateChange:true) }

