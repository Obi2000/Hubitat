// Hubitat driver for Govee RGB Strips using Cloud API
// Version 1.1.1
//
// 2021-01-07 -	Improved robustness of recalling device state
//				Fixed error with inital hue/sat commands if no data returned from server
// 2021-01-11 - Added option to use a 0-254 brightness range expected by some strips
// 2021-02-11 - API Key Alone generates device list, Then auto sets to your preferences last device in list
// 2021-03-29 - Added setColorTemperature(k,l,d) command for Hubitat 2.2.6, bug fixes in state reporting

metadata {
	definition(name: "Govee Immersion LED Strip", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
		capability "ColorControl"
		capability "ColorTemperature"
		capability "Light"
		capability "SwitchLevel"
		capability "ColorMode"
		capability "Refresh"
		
		attribute "colorName", "string"
        
        command "setColorTemperature", [[name:"Color temperature*", type:"NUMBER", description:"Color temperature in degrees Kelvin", constraints:["NUMBER"]]]
        
//		command "white"
//		command "ModeMusic"
//		command "ModeVideo"
//		command "DeviceInfo"
        
    }

	preferences {		
		section("Device Info") {
			input(name: "APIKey", type: "string", title: "User API Key", displayDuringSetup: true, required: true)
			input(name: "Model", type: "string", title: "Device Model Number", displayDuringSetup: true, required: false)
			input(name: "MACAddr", type: "string", title: "Device Mac address", displayDuringSetup: true, required: false)
			input(name: "aRngBright", type: "bool", title: "Alternate Brightness Range", description: "For devices that expect a brightness range of 0-254", defaultValue: false)
		}
		
	}
}

def parse(String description) {

}

def on() {
	sendEvent(name: "switch", value: "on")
	sendCommand("turn", "on")
}

def off() {
	sendEvent(name: "switch", value: "off")
	sendCommand("turn", "off")
}

def setColorTemperature(value,level=null,dur=null)
{
if(value>9000){value=9000}
if(value<2000){value=2000}
	sendEvent(name: "colorMode", value: "CT")
	//log.debug "ColorTemp = " + value
	def intvalue = value.toInteger()
	sendEvent(name: "switch", value: "on")	
	sendEvent(name: "colorTemperature", value: intvalue)
    
	sendCommand("colorTem", intvalue)
	setCTColorName(intvalue)
		
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





    
def setColor(value) {
	log.debug "HSBColor = "+ value
	if (value instanceof Map) {
		def h = value.containsKey("hue") ? value.hue : null
		def s = value.containsKey("saturation") ? value.saturation : null
		def b = value.containsKey("level") ? value.level : null
		setHsb(h, s, b)
	} else {
		log.warn "Invalid argument for setColor: ${value}"
    }
}

def setHsb(h,s,b)
{

	hsbcmd = [h,s,b]
//	log.debug "Cmd = ${hsbcmd}"

	sendEvent(name: "hue", value: "${h}")
	sendEvent(name: "saturation", value: "${s}")
	if(b!= device.currentValue("level")?.toInteger()){
		sendEvent(name: "level", value: "${b}")
		setLevel(b)
	}
	rgb = hubitat.helper.ColorUtils.hsvToRGB(hsbcmd)
	def rgbmap = [:]
	rgbmap.r = rgb[0]
	rgbmap.g = rgb[1]
	rgbmap.b = rgb[2]   
    
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "colorMode", value: "RGB")
		sendCommand("color", rgbmap)
    
}

def setHue(h)
{
    setHsb(h,device.currentValue( "saturation" )?:100,device.currentValue("level")?:100)
}

def setSaturation(s)
{
	setHsb(device.currentValue("hue")?:0,s,device.currentValue("level")?:100)
}

def setLevel(v,duration){
    setLevel(v)
}

def setLevel(v)
{
		if(v>0){
			sendEvent(name: "switch", value: "on")
			sendEvent(name: "level", value: v)
		}
		else{
			sendEvent(name: "switch", value: "off")		
		}
		
		if(aRngBright){v=incBrightnessRange(v)}
//		log.debug "Sent Brightness = ${v}"
		sendCommand("brightness", v)
}


//Turn Hubitat's 0-100 Brightness range to the 0-254 expected by some devices
def incBrightnessRange(v)
{
	v=v*(254/100)
	return Math.round(v)
}


//Go from 0-254 brightness range from some devices to Hubitat's 0-100 Brightness range. Maybe not needed?
def decBrightnessRange(v)
{
	v=v*(100/254)
	return Math.round(v)
}




def white() {

}


def DeviceInfo(){
log.debug "DEVICE INFORMATION"
	     def params = [
            uri   : "https://developer-api.govee.com",
            path  : '/v1/devices',
			headers: ["Govee-API-Key": settings.APIKey, "Content-Type": "application/json"],
        ]
    


try {

			httpGet(params) { resp ->

				//List each device assigned to current API key
				//log.debug resp.data
				resp.data.data.devices.each{
					deviceID = it.device
					deviceModel = it.model
					deviceName = it.deviceName
					log.debug "$deviceName	Address: $deviceID		Model: $deviceModel"
				}

				//Save the last device to preferences
				curDeviceID = resp.data.data.devices.last().device
				curDeviceModel = resp.data.data.devices.last().model

				device.updateSetting("Model",[value:curDeviceModel, type:"text"])
				device.updateSetting("MACAddr",[value:curDeviceID, type:"text"])

				runIn(2, 'setupDevice')
				return resp.data
			}
			
	} catch (groovyx.net.http.HttpResponseException e) {
		log.error "Error: e.statusCode ${e.statusCode}"
		log.error "${e}"
		
		return 'unknown'
	}    
}



def getDeviceSupport(){
	    def params = [
			uri   : "https://developer-api.govee.com",
			path  : '/v1/devices',
			headers: ["Govee-API-Key": settings.APIKey, "Content-Type": "application/json"],
			query: [device: settings.MACAddr, model: settings.Model],
		]
    


try {

			httpGet(params) { resp ->

				state.hasRetrievable = resp.data.data.devices.find({it.device==settings.MACAddr}).retrievable

				return resp.data
			}
			
	} catch (groovyx.net.http.HttpResponseException e) {
		log.error "Error: e.statusCode ${e.statusCode}"
		log.error "${e}"
		
		return 'unknown'
	}
}



private def sendCommand(String command, payload) {


     def params = [
            uri   : "https://developer-api.govee.com",
            path  : '/v1/devices/control',
			headers: ["Govee-API-Key": settings.APIKey, "Content-Type": "application/json"],
            contentType: "application/json",      
			body: [device: settings.MACAddr, model: settings.Model, cmd: ["name": command, "value": payload]],
        ]
    

try {

			httpPut(params) { resp ->
				
				//log.debug "response.data="+resp.data
				
				return resp.data
		
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		log.error "Error: e.statusCode ${e.statusCode}"
		log.error "${e}"
		
		return 'unknown'
	}
}


def getDeviceState(){
	
		def params = [
			uri   : "https://developer-api.govee.com",
			path  : '/v1/devices/state',
			headers: ["Govee-API-Key": settings.APIKey, "Content-Type": "application/json"],
			query: [device: settings.MACAddr, model: settings.Model],
        ]
    


try {

			httpGet(params) { resp ->

				log.debug resp.data.data.properties
				varPower = resp.data.data.properties.find({it.powerState})?.powerState
				varBrightness = resp.data.data.properties.find({it.brightness})?.brightness
				mapColor = resp.data.data.properties.find({it.color})?.color                
				varCT = resp.data.data.properties.find({it.colorTemInKelvin})?.colorTemInKelvin

                //if(aRngBright){varBrightness=decBrightnessRange(varBrightness)}
				//log.debug "Recvd Brightness = ${varBrightness}"
				
				sendEvent(name: "switch", value: varPower)
				
				
				if(varBrightness){
					sendEvent(name: "level", value: varBrightness)
				}
				
				
                if(varCT){
					sendEvent(name: "colorTemperature", value: varCT)
					sendEvent(name: "colorMode", value: "CT")
					setCTColorName(varCT)					
                }
                
				if(mapColor){
					r=mapColor.r
					g=mapColor.g
					b=mapColor.b
					HSVlst=hubitat.helper.ColorUtils.rgbToHSV([r,g,b])
					hue=HSVlst[0].toInteger()
					sat=HSVlst[1].toInteger()
					sendEvent(name: "hue", value: hue)
					sendEvent(name: "saturation", value: sat)
					sendEvent(name: "colorMode", value: "RGB")
				
				}
				
				return resp.data
			}
			
	} catch (groovyx.net.http.HttpResponseException e) {
		log.error "Error: e.statusCode ${e.statusCode}"
		log.error "${e}"
		
		return 'unknown'
	}
}

def poll() {
	refresh()
}

def refresh() {
    if(state.hasRetrievable){
        getDeviceState()
    }
}

def updated() {
//get devices or get specific device info
if(!settings.MACAddr || !settings.Model)
	{
		DeviceInfo()
	}
	else{
		setupDevice()
	}

}

def setupDevice(){
		getDeviceSupport()
		refresh()
}

def installed(){
    sendEvent(name: "hue", value: 0)
    sendEvent(name: "saturation", value: 100)
    sendEvent(name: "level", value: 100)   
}