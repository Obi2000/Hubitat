
//Tasmota HttpHook Driver for RGB Light Switch
//By Obi2000
//
//so73  1  decouple relay & enable buttons
//so32  8  time to hold(0.1s) default 40
//so1	1  disable inadvertent reset due to hold
//use rule to tie buttons to relays
//
//1.0.1


metadata {
	definition(name: "Tasmota RGB Light Switch", namespace: "Obi2000", author: "Obi2000") {
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

	def cdSw=getChildDevice("$device.id-Switch1")
	def cdLt=getChildDevice("$device.id-Light1")
	
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
        cdSw.parse([[name:"switch", value:"${tasdata.POWER1.toLowerCase()}", descriptionText:"Switch $tasdata.POWER1"]])
    }  

	if (tasdata.containsKey("POWER2")) {
        cdLt.parse([[name:"switch", value:"${tasdata.POWER2.toLowerCase()}", descriptionText:"Light ${tasdata.POWER2}"]])
    }

	if (tasdata.containsKey("Color")) {
		if(tasdata.Color.toString().contains(",")){
            List rgbColor = tasdata.Color.tokenize(",")
			HSBColor = hubitat.helper.ColorUtils.rgbToHSV([rgbColor[0].toInteger(),rgbColor[1].toInteger(),rgbColor[2].toInteger()])
		}
		else{
			rgbColor = hubitat.helper.ColorUtils.hexToRGB("#${tasdata.Color}")
			HSBColor = hubitat.helper.ColorUtils.rgbToHSV(rgbColor)
		}

        hue=HSBColor[0]
		sat=HSBColor[1]
		bri=HSBColor[2].intValue()
		cdLt.parse([[name:"colorMode", value:"RGB"]])
		cdLt.parse([[name:"hue", value:"${hue}"]])
		cdLt.parse([[name:"saturation", value:"${sat}"]])
		cdLt.parse([[name:"level", value:"${bri}"]])
    
    }
	
//	if (tasdata.containsKey("HSBColor")) {
//		List HSBColor = tasdata.HSBColor.tokenize(",")
//       hue=HSBColor[0]
//        sat=HSBColor[1]
//        bri=HSBColor[2]
//        cdLt.parse([[name:"colorMode", value:"RGB"]])
//        cdLt.parse([[name:"hue", value:"${hue}"]])
//        cdLt.parse([[name:"saturation", value:"${sat}"]])
//        cdLt.parse([[name:"level", value:"${bri}"]])
//    }
    
		
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
    


    
	if (tasdata.containsKey("Dimmer")) {
		cdLt.parse([[name:"level", value:tasdata.Dimmer, descriptionText:"Dimmer $tasdata.Dimmer"]])
	}
	

}


//turn all children on, Power0
def on() {
	sendCommand("Power0", "On")
	
}

//turn all children off
def off() {
	sendCommand("Power0", "Off")
}




//def setLevel(v)
//{
//    setLevel(v, 0)
//}

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
  



cdDNI="$device.id-Switch1"                    
def curCD = getChildDevice(cdDNI)
if (curCD == null) {
	addChildDevice("hubitat", "Generic Component Switch", cdDNI, [name: "${device.displayName}-Switch1",isComponent: false])
	log.info "Child Switch1 added"   
					}

curCD = null
cdDNI="$device.id-Light1"                    
curCD = getChildDevice(cdDNI)
if (curCD == null) {
	addChildDevice("hubitat", "Generic Component RGBW", cdDNI, [name: "${device.displayName}-Light1",isComponent: false])
	log.info "Child Light1 added"   
	getChildDevice(cdDNI).updateSetting("txtEnable",[value:false, type:"bool"])
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
	if (cd.deviceNetworkId == "${device.id}-Switch1"){sendCommand("Power1", "On")}
	if (cd.deviceNetworkId == "${device.id}-Light1"){sendCommand("Power2", "On")}
}

def componentOff(cd){
	if (cd.deviceNetworkId == "${device.id}-Switch1"){sendCommand("Power1", "Off")}
	if (cd.deviceNetworkId == "${device.id}-Light1"){sendCommand("Power2", "Off")}
}


def componentSetColor(cd,HSBColor)
{


		RGBColor = hubitat.helper.ColorUtils.hsvToRGB([HSBColor.hue,HSBColor.saturation,HSBColor.level])
		hexColor = hubitat.helper.ColorUtils.rgbToHEX(RGBColor).minus("#")

    //log.debug HSBColor
    //HSBColor.hue
    //HSBColor.saturation
    //HSBColor.level
    //hue=HSBColor.hue*(359/100)
	//sendCommand("HSBColor", "${hue},${HSBColor.saturation},${HSBColor.level}")
	sendCommand("Color", hexColor)
}

def componentSetHue(cd,hue){
    aHue=hue*(359/100)
	sendCommand("HsbColor1", "${aHue}")
}

def componentSetSaturation(cd,sat){
	sendCommand("HsbColor2", "${sat}")
}

def componentSetColorTemperature(cd,ct,level=null,dur=null){
	hexColor = "FFA27A"   //This light is realy blue tinted
	sendCommand("Color", hexColor)
}


def componentSetLevel(cd,v,d=0){
	setLevel(v)
}

//Dimmer >
void componentStartLevelChange(cd,dir){
	if(dir=="up"){setLevel(100)}
	if(dir=="down"){setLevel(0)}
}

//Dimmer !
void componentStopLevelChange(cd){

}


def recreateChildDevices(){
	createChildDevices()
}


