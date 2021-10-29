//Tasmota HttpHook Driver for RGBCT Bulb
//By Obi2000
//

//
//1.0.2 - Pre-staging support


metadata {
	definition(name: "Tasmota RGBCT Bulb", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
		capability "ColorControl"
        capability "ColorTemperature"
		capability "Light"
		capability "SwitchLevel"
        capability "ColorMode"
        capability "Refresh"
		
		capability "Change Level"
    }

	preferences {		
		section("Switch Info") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
			input(name: "port", type: "number", title: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
			input(name: "so20", type: "bool", title: "so20 - Light Pre-staging", displayDuringSetup: true)
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

	if (tasdata.containsKey("CT")&&(!tasdata.containsKey("Color"))) {
		//Color Temperature
		sendEvent(name: "colorMode", value: "CT")
		kelvin=Math.round(1000000/tasdata.CT)

		sendEvent(name: "colorTemperature", value: kelvin)
		setCTColorName(kelvin)
	}

	if (tasdata.containsKey("Color")) {
	
		if(tasdata.Color.startsWith("000000")){
		//Color Temperature
			sendEvent(name: "colorMode", value: "CT")
			kelvin=Math.round(1000000/tasdata.CT)

			sendEvent(name: "colorTemperature", value: kelvin)
			setCTColorName(kelvin)			
		}
		else{
		//RGB
			if(tasdata.Color.toString().contains(",")){
				List rgbColor = tasdata.Color.tokenize(",")
				HSBColor = hubitat.helper.ColorUtils.rgbToHSV([rgbColor[0].toInteger(),rgbColor[1].toInteger(),rgbColor[2].toInteger()])
			}
			else{
				rgbColor = hubitat.helper.ColorUtils.hexToRGB("#${tasdata.Color.take(6)}")
				HSBColor = hubitat.helper.ColorUtils.rgbToHSV(rgbColor)
			}

			hue=HSBColor[0]
			sat=HSBColor[1]
			bri=HSBColor[2].intValue()
			sendEvent(name:"colorMode", value:"RGB")
			sendEvent(name:"hue", value:"${hue}")
			sendEvent(name:"saturation", value:"${sat}")
			sendEvent(name:"level", value:"${bri}")
		}
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
    
		
   

	
	if (tasdata.containsKey("Dimmer")) {
		sendEvent(name:"level", value:tasdata.Dimmer, descriptionText:"Dimmer $tasdata.Dimmer")
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

    if(so20!=null){
		upStr = "so20%20${so20}"
		sendCommand(upStr)
		}  //;so20 prestaging

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




def setColor(HSBColor)
{

	RGBColor = hubitat.helper.ColorUtils.hsvToRGB([HSBColor.hue,HSBColor.saturation,HSBColor.level])
	hexColor = hubitat.helper.ColorUtils.rgbToHEX(RGBColor).minus("#")

	sendCommand("Color", hexColor)
}

def setHue(hue){
    aHue=hue*(359/100)
	sendCommand("HsbColor1", "${aHue}")
}

def setSaturation(sat){
	sendCommand("HsbColor2", "${sat}")
}

def setColorTemperature(CT,level=null,dur=null){
	mired = Math.round(1000000/CT)
    if(mired<153){mired=153}
    if(mired>500){mired=500}
	sendCommand("CT", mired)
	
	if(level!=null){
		setLevel(level,dur)
	}
}


def setCTColorName(value)
{
		if (value < 2600) {
			sendEvent(name: "colorName", value: "Warm White")
		}
		else if (value < 3500) {
			sendEvent(name: "colorName", value: "Incandescent")
		}
		else if (value < 4500) {
			sendEvent(name: "colorName", value: "White")
		}
		else if (value < 5500) {
			sendEvent(name: "colorName", value: "Daylight")
		}
		else if (value >=  5500) {
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



