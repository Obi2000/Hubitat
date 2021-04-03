
//Tasmota HttpHook Driver for Treatlife Fan and Dimmer (DS03)
//By Obi2000
//
//2.0.5 - Dimmer rate limiter for mirroring to groups


metadata {
	definition(name: "Treatlife Fan Control and Dimmer", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
        capability "Refresh"
		
		//command "recreateChildDevices"
    }

	preferences {		
		section("Switch Info") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
			input(name: "port", type: "number", title: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
            input(name: "rateLimit", type: "number", title: "Dimmer Rate Limit", displayDuringSetup: true, required: true, defaultValue: 500)
		}
	}
}


def parse(String description) {

	def cdFan=getChildDevice("$device.id-Fan1")
	def cdDim=getChildDevice("$device.id-Dim1")
	def tasdata = [:]
	def msg = parseLanMessage(description)
	if(msg.headers.containsKey("POST / HTTP/1.1")){
		def slurper = new groovy.json.JsonSlurper()
		try {
			tasdata = slurper.parseText(msg.body)
		} catch (e) {
		}
		}


	if (tasdata.containsKey("POWER1")) {
        cdFan.parse([[name:"switch", value:"${tasdata.POWER1.toLowerCase()}", descriptionText:"Fan $tasdata.POWER1"]])
    }  

	if (tasdata.containsKey("POWER2")) {
        cdDim.parse([[name:"switch", value:"${tasdata.POWER2.toLowerCase()}", descriptionText:"Dimmer ${tasdata.POWER2}"]])
    }
	
	if (tasdata.containsKey("Dimmer")) {
        runInMillis(2000,delayDimEvt,[data:tasdata.Dimmer])     //Catch last dim event if skipped
		Long timeNow = now();
		if ((timeNow - state.timeStamp) > rateLimit){
		    cdDim.parse([[name:"level", value:"$tasdata.Dimmer", descriptionText:"Dimmer $tasdata.Dimmer"]])
			state.timeStamp = timeNow as Long;
			}
    }
	
	if (tasdata.containsKey("TuyaEnum4")) {
		//log.debug "FanSpeed:" + tasdata.TuyaEnum4
		//sendEvent(name: "speed", value: "${enum2Speed(tasdata.TuyaEnum4)}")
		cdFan.parse([[name:"speed", value:"${enum2Speed(tasdata.TuyaEnum4)}", descriptionText:"Fan ${enum2Speed(tasdata.TuyaEnum4)}"]])
    }

	//from refresh
	if (tasdata.containsKey("TuyaEnum")) {
		//log.debug "FanSpeed:" + tasdata.TuyaEnum.Enum4
		//sendEvent(name: "speed", value: "${enum2Speed(tasdata.TuyaEnum.Enum4)}")
		cdFan.parse([[name:"speed", value:"${enum2Speed(tasdata.TuyaEnum.Enum4)}", descriptionText:"Fan ${enum2Speed(tasdata.TuyaEnum.Enum4)}"]])
    }

}

def delayDimEvt(Dimmer){
    def cdDim=getChildDevice("$device.id-Dim1")
    cdDim.parse([[name:"level", value:"$Dimmer", descriptionText:"Dimmer $Dimmer"]])
}

//turn all children on
def on() {
    //sendEvent(name: "switch", value: "on")
	//sendCommand("Power2", "On")
	sendCommand("BACKLOG%20Power2%20On%3BPower1%20On")
}

//turn all children off
def off() {
    //sendEvent(name: "switch", value: "off")
	//sendCommand("Power2", "Off")
	sendCommand("BACKLOG%20Power2%20Off%3BPower1%20Off")
}


def fan_on() {
    //sendEvent(name: "switch", value: "on")
	sendCommand("Power1", "On")
}

def fan_off() {
    //sendEvent(name: "switch", value: "off")
	sendCommand("Power1", "Off")
}

//set fan speed by list control
def setSpeed(speed) {
	log.debug speed
	switch(speed){
		case "auto":
			break
		case "on":
			fan_on()
			break
		case "off":
			fan_off()
			break
			
		default:
			fanval=speed2Enum(speed)
			log.debug fanval
			sendCommand("TuyaEnum4", fanval)
			break
	}
}

//set fan speed by dimmer value
def setSpeedLvl(v){
	speed="low"

	if (v==0){speed="off"}
	if (v>=26){speed="medium-low"}
	if (v>=51){speed="medium-high"}
	if (v>=76){speed="high"}

	setSpeed(speed)
}

def speed2Enum(speed){
	fan=3
	if (speed=="low"){fan=0}
	if (speed=="medium-low"){fan=1}
	if (speed=="medium"){fan=2}
	if (speed=="medium-high"){fan=2}
	if (speed=="high"){fan=3}

return fan
}


def enum2Speed(fan){
	speed="auto"

	if (fan==0){speed="low"}
	if (fan==1){speed="medium-low"}
	if (fan==2){speed="medium-high"}
	if (fan==3){speed="high"}

return speed
}




def setLevel(v)
{
    setLevel(v, 0)
}

def setLevel(v, duration)
{
	sendEvent(name: "level", value: "${v}")
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
createChildDevices()



}



def updated() {
state.dni=convertIPtoHex(ipAddress)
device.deviceNetworkId = state.dni
state.timeStamp = now() as Long;
refresh()
}

def refresh(){
	sendCommand("BACKLOG%20state%3Btuyaenum4")
}



def createChildDevices(){
	addChildDevice("hubitat", "Generic Component Dimmer", "$device.id-Dim1", [name: "${device.displayName}-Dimmer",isComponent: false])
	addChildDevice("hubitat", "Generic Component Fan Control", "$device.id-Fan1", [name: "${device.displayName}-Fan",isComponent: false])
	getChildDevice("$device.id-Dim1").updateSetting("txtEnable",[value:false, type:"bool"])
}

def configure() {

}


private def sendCommand(String command){
	sendCommand(command,null)
}


private def sendCommand(String command, payload) {
if(payload!=null){payload = payload.toString()}
log.debug "cmd:"+command+"pay:"+payload
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

log.debug path    
	def result = new hubitat.device.HubAction(
		method: "GET",
		path: path,
		headers: [
			HOST: "${ipAddress}:${port}"
		]
	)

   sendHubCommand(result)
}

def componentSetSpeed(cd,speed){
	setSpeed(speed)
}

def componentRefresh(cd){
  refresh()
}

def componentOn(cd){
   sendCommand("Power2", "On")
 //   getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
}

def componentOff(cd){

	sendCommand("Power2", "Off")
//    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
}

def componentSetLevel(cd,v){
	componentSetLevel(cd,v,0)
}

def componentSetLevel(cd,v,d){
	if (cd.deviceNetworkId == "${device.id}-Dim1"){setLevel(v)}
	if (cd.deviceNetworkId == "${device.id}-Fan1"){setSpeedLvl(v)} 	
}

//Dimmer >
void componentStartLevelChange(cd,dir){
	if(dir=="up"){setLevel(100)}
	if(dir=="down"){setLevel(0)}
}

//Dimmer !
void componentStopLevelChange(cd){

}

//cycle through fan speeds 
def componentCycleSpeed(cd){
	def cdFan=getChildDevice("$device.id-Fan1")
	speed=cdFan.currentValue("speed")

	//turn fan speed into an int and ++
	fanE=speed2Enum(speed)
	fanE = fanE+1
	if(fanE>3){fanE=0}
	
	setSpeed(enum2Speed(fanE))
}

def recreateChildDevices(){
	createChildDevices()
}


