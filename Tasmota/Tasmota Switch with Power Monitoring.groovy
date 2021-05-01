
//Tasmota HttpHook Driver for Switch with Power Monitoring
//By Obi2000
//
//Follow the power calibration procedure at https://tasmota.github.io/docs/Power-Monitoring-Calibration/
//
//
//1.0.5


metadata {
	definition(name: "Tasmota Switch with Power Monitoring", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
        capability "Refresh"
		
		//command "recreateChildDevices"
    }

	preferences {		
		section("Device Info") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
			input(name: "port", type: "number", title: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
            input(name: "powerDelta", type: "number",title: "Power Change Report Value",
                description: "Report Power change greater than X watts, offset by 100. 0=Disabled, 105=every 5W, 175=every 75W, (1-100 is a % change)", displayDuringSetup: true, required: true, defaultValue: 105)
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

    if(tasdata.containsKey("StatusSNS")) {tasdata=tasdata.StatusSNS}
    
	if(tasdata.containsKey("ENERGY")) {

		//sendEvent(name: "power", value: power)
		//sendEvent(name: "amperage", value: power)
		//sendEvent(name: "voltage", value: power)
		//sendEvent(name: "energy", value: power)
		

		getChildDevices().each{
		//log.info it
		it.parse([[name:"power", value:"${tasdata.ENERGY.Power}", descriptionText:"power $tasdata.ENERGY.Power"]])
		it.parse([[name:"amperage", value:"${tasdata.ENERGY.Current}", descriptionText:"amperage $tasdata.ENERGY.Current"]])
		it.parse([[name:"voltage", value:"${tasdata.ENERGY.Voltage}", descriptionText:"voltage $tasdata.ENERGY.Voltage"]])			
		it.parse([[name:"energy", value:"${tasdata.ENERGY.Total}", descriptionText:"energy $tasdata.ENERGY.Total"]])	
		}
	}
	
	
	powerMap=tasdata.findAll{it.key.toString().contains("POWER")}
                
    powerMap.each{
        cdNum = it.key.minus("POWER")
        if(!cdNum){cdNum=1}
        cdCur = getChildDevice("$device.id-${cdNum}")
        cdCur.parse([[name:"switch", value:"${it.value.toLowerCase()}", descriptionText:"${cdCur.name} $it.value"]])
    }
	

}


//turn all children on, Power0
def on() {
    //sendEvent(name: "switch", value: "on")
	sendCommand("Power0", "On")
	
}

//turn all children off
def off() {
    //sendEvent(name: "switch", value: "off")
	sendCommand("Power0", "Off")
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

createChildDevices()

def upStr = "BACKLOG"
    upStr += "%20powerdelta%20${powerDelta}"
log.debug upStr
sendCommand(upStr)
    
 
refresh()
}

def refresh(){
	sendCommand("state")
    sendCommand("status%208") //status 8
}



def createChildDevices(){
def powerMap=[:]
def numPower = 0    

//Get number of children
//call status 11
//create a child device for each instance of POWER    
def params = [
    uri   : "http://${ipAddress}:${port}/cm",
    queryString  : 'cmnd=status%2011',
    ]
    
try {

			httpPut(params) { resp ->
				
				powerMap=resp.data.StatusSTS.findAll{it.key.toString().contains("POWER")}
                
                powerMap.eachWithIndex {it, index ->
                    cdNum=index+1
                    cdDNI="$device.id-${cdNum}"
                    
                    def curCD = getChildDevice(cdDNI)
                    
                    if (curCD == null) {
                        addChildDevice("hubitat", "Generic Component Metering Switch", cdDNI, [name: "${device.displayName}-Switch${cdNum}",isComponent: false])
                        log.info "Child Switch${cdNum} added"   
                    }               
                }
                
               
                
                
				return resp.data
			}
	} catch (groovyx.net.http.HttpResponseException e) {
		log.error "Error: e.statusCode ${e.statusCode}"
		log.error "${e}"
		
		return 'unknown'
	}





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


def componentRefresh(cd){
	refresh()
}

def componentOn(cd){
    cdNum=cd.deviceNetworkId.minus("${device.id}-")
    sendCommand("Power${cdNum}", "On")
}

def componentOff(cd){
	cdNum=cd.deviceNetworkId.minus("${device.id}-")
	sendCommand("Power${cdNum}", "Off")
}




def recreateChildDevices(){
	createChildDevices()
}


