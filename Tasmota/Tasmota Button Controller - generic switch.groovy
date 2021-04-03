
//Tasmota HttpHook Driver for Button Control on Switches
//By Obi2000
//
//so73  1  decouple relay & enable buttons
//so32  8  time to hold(0.1s) default 40
//so1	1  disable inadvertent reset due to hold
//use rule to tie buttons to relays
//
//1.0.5


metadata {
	definition(name: "Tasmota Button Controller Generic Switch", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
        capability "Refresh"
		capability "PushableButton"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		
		//command "recreateChildDevices"
    }

	preferences {		
		section("Switch Info") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
			input(name: "port", type: "number", title: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
            input(name: "numButtons", type: "number", title: "Number of Buttons", displayDuringSetup: true, required: true, defaultValue: 1)
            input(name: "so73", type: "bool", title: "so73 - Decouple Relays/Enable Buttons", displayDuringSetup: true, required: true, defaultValue: true)
            input(name: "so32", type: "number", title: "so32 - Time to trigger hold(0.1s), Warning reset due to hold is 10x this value", displayDuringSetup: true)
            input(name: "so1", type: "bool", title: "so1 - Disable reset due to hold", displayDuringSetup: true)
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



	powerMap=tasdata.findAll{it.key.toString().contains("POWER")}
                
    powerMap.each{
        cdNum = it.key.minus("POWER")
        if(!cdNum){cdNum=1}
        cdCur = getChildDevice("$device.id-${cdNum}")
        cdCur.parse([[name:"switch", value:"${it.value.toLowerCase()}", descriptionText:"${cdCur.name} $it.value"]])
    }
    
	buttonMap=tasdata.findAll{it.key.toString().contains("Button")}
                
    buttonMap.each{
        button = it.key.minus("Button").toInteger()
        action = it.value.Action
        
        if(action=="TRIPLE"){
            button=numButtons+button
            action="SINGLE"    
        }
        
        if(action=="QUAD"){
            button=numButtons+button
            action="DOUBLE"    
        }
		
		if(action=="SINGLE"){
			sendEvent(name: "pushed", value: button, isStateChange: true )
		}
		
		if(action=="DOUBLE"){
			sendEvent(name: "doubleTapped", value: button, isStateChange: true )
		}

		if(action=="HOLD"){
			sendEvent(name: "held", value: button, isStateChange: true )
		}		
		
        log.info "Button:$button Action:$action"

    }  
    

	
//	if (tasdata.containsKey("Dimmer")) {
//		    //cdDim.parse([[name:"level", value:"$tasdata.Dimmer", descriptionText:"Dimmer $tasdata.Dimmer"]])
//    }
	

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




//def setLevel(v)
//{
//    setLevel(v, 0)
//}

//def setLevel(v, duration)
//{
//	sendEvent(name: "level", value: "${v}")
//    sendCommand("Dimmer", "${v}")
//}

private String convertIPtoHex(ipAddress) { 
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format('%04x', port.toInteger())
	return hexport
}

def installed() {
    sendEvent(name: "numberOfButtons", value: 1*2)
}



def updated() {
state.dni=convertIPtoHex(ipAddress)
device.deviceNetworkId = state.dni

createChildDevices()

def upStr = "BACKLOG"
    upStr += "%20so73%20${so73}"
    if(so1!=null){upStr += "%3Bso1%20${so1}"}  //;so1 true
    if(so32!=null){upStr += "%3Bso32%20${so32}"}  //;so32 8
log.debug upStr
sendCommand(upStr)

    
sendEvent(name: "numberOfButtons", value: numButtons*2)
 
refresh()
}

def refresh(){
	sendCommand("state")
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
                        addChildDevice("hubitat", "Generic Component Switch", cdDNI, [name: "${device.displayName}-Switch${cdNum}",isComponent: false])
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






	//addChildDevice("hubitat", "Generic Component Fan Control", "$device.id-Fan1", [name: //"${device.displayName}-Fan",isComponent: false])
	//getChildDevice("$device.id-Dim1").updateSetting("txtEnable",[value:false, type:"bool"])
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

//def componentSetLevel(cd,v){
//	componentSetLevel(cd,v,0)
//}

//def componentSetLevel(cd,v,d){
//	if (cd.deviceNetworkId == "${device.id}-Dim1"){setLevel(v)}
//	if (cd.deviceNetworkId == "${device.id}-Fan1"){setSpeedLvl(v)} 	
//}

//Dimmer >
//void componentStartLevelChange(cd,dir){
//	if(dir=="up"){setLevel(100)}
//	if(dir=="down"){setLevel(0)}
//}

//Dimmer !
//void componentStopLevelChange(cd){

//}


def recreateChildDevices(){
	createChildDevices()
}


