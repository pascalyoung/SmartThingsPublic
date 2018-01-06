/* **DISCLAIMER**
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * Without limitation of the foregoing, Contributors/Regents expressly does not warrant that:
 * 1. the software will meet your requirements or expectations;
 * 2. the software or the software content will be free of bugs, errors, viruses or other defects;
 * 3. any results, output, or data provided through or generated by the software will be accurate, up-to-date, complete or reliable;
 * 4. the software will be compatible with third party software;
 * 5. any errors in the software will be corrected.
 * The user assumes all responsibility for selecting the software and for the results obtained from the use of the software. The user shall bear the entire risk as to the quality and the performance of the software.
 */ 

def clientVersion() {
    return "02.05.03"
}
 
/**
 *  Mode Change Thermostat Temperature
 *
 * Copyright RBoy Apps, redistribution or reuse of code is not allowed without permission
 *
 * 2017-11-13 - (v02.05.03) Optimization, don't loop through mode settings which aren't current
 * 2017-11-12 - (v02.05.02) Fix for remote temperature sensor with multiple modes
 * 2017-9-9 - (v02.05.01) Updated min temp to 60F for better GoControl thermostat compatibility
 * 2017-9-5 - (v02.05.00) Updated min/max thresholds for remote sensors to be compatible with new ST thermostat device handler (deadZones)
 * 2017-2-1 - (v2.4.1) Bugfix for temporary hold when using remote temperature sensors
 * 2017-1-27 - (v2.4.0) Added support for multiple remote temperature sensors per thermostat and for temporary hold mode for remote temperature sensor
 * 2016-11-5 - Added support for automatic code update notifications
 * 2016-4-23 - Fixed bug with settings in individual temperature for each thermostat for a single mode and various UI fixes
 * 2016-3-5 - Fixed bug with settings all modes temperature when in multi mode configuration mode
 * 2016-1-26 - Fixed a bug with modes selection
 * 2016-1-26 - Combined the single and individual temperature apps into a single app through a configurable menu
 * 2015-5-18 - Added support for individual mode temperatures
 * 2015-5-17 - Initial code
 *
 */
definition(
		name: "Ultimate Mode Change Thermostat Temperature",
		namespace: "rboy",
		author: "RBoy Apps",
		description: "Change the thermostat(s) temperature on a mode(s) change",
    	category: "Green Living",
    	iconUrl: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving.png",
    	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@2x.png",
    	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@3x.png")

preferences {
	page(name: "setupApp")
    page(name: "tempPage")
}

def setupApp() {
    dynamicPage(name: "setupApp", title: "Ultimate Mode Change Thermostat Temperature v${clientVersion()}", install: false, uninstall: true, nextPage: "tempPage") {
        section("Select thermostat(s)") {
            input "thermostats", title: "Select thermostat(s) to configure", "capability.thermostat", required: true, multiple: true
        }

        section("Select Mode(s)") {
            input "modes", "mode", title: "Select mode(s) to configure", multiple: true, required: false
        }
        
        section() {
            label title: "Assign a name for this SmartApp (optional)", required: false
            input name: "disableUpdateNotifications", title: "Don't check for new versions of the app", type: "bool", required: false
        }
    }
}

def tempPage() {
    dynamicPage(name:"tempPage", title: "Configure Temperature for modes and thermostats", uninstall: true, install: true) {
        section() {
            input name: "multiTempModes", type: "bool", title: "Separate temperatures for each selected mode?", description: "Do you want to define different temperatures for each selected mode?", required: true, submitOnChange: true
            input name: "multiTempThermostat", type: "bool", title: "Separate temperatures for each selected thermostat?", description: "Do you want to define different temperatures for each thermostat?", required: true, submitOnChange: true
        }
    	def maxModes = multiTempModes ? (modes == null ? 1 : modes.size()) : 1
    	for (int j = 0; j < maxModes; j++) {
        	def modeName = multiTempModes ? (modes == null ? "All" : modes[j]) : "All"
            if (modeName != "All") {
                section() {
                    paragraph title: "$modeName Mode Thermostat Settings", "Enter the heat/cool temperatures for thermostats in $modeName mode"
                }
            }
            def maxThermostats = multiTempThermostat ? thermostats.size() : 1
            for (int i = 0; i < maxThermostats; i++) {
                def heat = settings."opHeatSet${i}${j}"
                def cool = settings."opCoolSet${i}${j}"
                log.debug "$modeName Mode ${multiTempThermostat ? thermostats[i] : "All Thermostats"} Heat: $heat, Cool: $cool"

                section("${multiTempThermostat ? thermostats[i] : "All Thermostats"} heat/cool temperatures") {
                    input name: "opHeatSet${i}${j}", type: "decimal", defaultValue: "${heat ?: ""}", title: "When Heating", description: "Heating temperature for mode", required: true
                    input name: "opCoolSet${i}${j}", type: "decimal", defaultValue: "${cool ?: ""}", title: "When Cooling", description: "Cooling temperature for mode", required: true
                    if ((settings."remoteTemperatureSensor${i}${j}"*.currentTemperature)?.count { it } > 1) {
                        paragraph title: "You have selected multiple remote sensors, the average temperature across the sensors will be used", required: true, ""
                    }
                    input "remoteTemperatureSensor${i}${j}", "capability.temperatureMeasurement", title: "Remote temperature sensor", description: "Use remote temperature sensor to control ${multiTempThermostat ? thermostats[i] : "All Thermostats"} for $modeName mode", required: false, multiple:true, submitOnChange: true
                    if (settings."remoteTemperatureSensor${i}${j}") {
                        input "threshold${i}${j}", "decimal", title: "Temperature swing (precision)", defaultValue: "1.0", required: true // , range: "0.5..5.0" causes Android 2.0.7 to crash, TODO: add this in later
                    }
                }
            }
        }
    }
}

// Globals
private getMIN_HEAT_TEMP_F() { 45 }
private getMAX_HEAT_TEMP_F() { 90 }
private getMIN_COOL_TEMP_F() { 60 }
private getMAX_COOL_TEMP_F() { 95 }
private getMIN_HEAT_TEMP_C() { 8 }
private getMAX_HEAT_TEMP_C() { 32 }
private getMIN_COOL_TEMP_C() { 15 }
private getMAX_COOL_TEMP_C() { 35 }

def installed()
{
	subscribeToEvents()
}

def updated()
{
    unsubscribe()
    unschedule()
	subscribeToEvents()
}

def subscribeToEvents() {
    // Check for new versions of the code
    def random = new Random()
    Integer randomHour = random.nextInt(18-10) + 10
    Integer randomDayOfWeek = random.nextInt(7-1) + 1 // 1 to 7
    schedule("0 0 " + randomHour + " ? * " + randomDayOfWeek, checkForCodeUpdate) // Check for code updates once a week at a random day and time between 10am and 6pm

    def maxModes = multiTempModes ? (modes == null ? 1 : modes.size()) : 1
    for (int j = 0; j < maxModes; j++) {
        def maxThermostats = multiTempThermostat ? thermostats.size() : 1
        for (int i = 0; i < maxThermostats; i++) {
            subscribe(settings."remoteTemperatureSensor${i}${j}", "temperature", remoteChangeHandler) // Handle changes in remote temperature sensor and readjust thermostat
        }
    }

    subscribe(thermostats, "heatingSetpoint", thermostatSetTempHandler) // Handle changes in manual thermostat heating setpoint temperature changes for Hold mode
    subscribe(thermostats, "coolingSetpoint", thermostatSetTempHandler) // Handle changes in manual thermostat cooling setpoint temperature changes for Hold mode
    subscribe(location, modeChangeHandler)
    
    log.debug "Selected Modes -> $modes, settings -> $settings"
    
    // Kick start if we are in the right mode
    modeChangeHandler([value:location.mode])
}

// Handle manual changes in thermostat setpoint for temporary Hold mode when using remote sensors
def thermostatSetTempHandler(evt) {
    log.debug "Received temperature set notification from ${evt.device.displayName}, name: ${evt.name}, value: ${evt.value}, mode: ${location.mode}"

	// Since we are manually entering the mode, check the mode before continuing since these aren't registered with the system (thanks @bravenel)
    if (modes && !modes.contains(location.mode)) {
    	log.debug "${location.mode} mode not in list of selected modes $modes"
    	return
    }
    
    // Lets find which remote sensors are linked to this thermostat and see if we are in hold more or not
    def maxModes = multiTempModes ? (modes == null ? 1 : modes.size()) : 1
    for (int j = 0; j < maxModes; j++) {
        def modeName = multiTempModes ? (modes == null ? "All" : modes[j]) : "All"
        if (modes && !modes[j]?.contains(location.mode)) {
            continue // this isn't our mode, ignore it
        }
        def maxThermostats = multiTempThermostat ? thermostats.size() : 1
        for (int i = 0; i < maxThermostats; i++) {
            if(thermostats[i].displayName.contains(evt.device.displayName)) {
                def remoteTemperatureSensor = settings."remoteTemperatureSensor${i}${j}"
                if (!remoteTemperatureSensor) {
                    log.trace "No remote temperature sensor connected to thermostat ${evt.device.displayName} for mode ${modeName}"
                    return
                } else {
                    def coolingSetpoint = settings."opCoolSet${i}${j}"
                    def heatingSetpoint = settings."opHeatSet${i}${j}"
                    def locationScale = getTemperatureScale()
                    def maxCTemp
                    def minCTemp
                    def maxHTemp
                    def minHTemp
                    if (locationScale == "C") {
                        minCTemp = MIN_COOL_TEMP_C // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
                        maxCTemp = MAX_COOL_TEMP_C // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
                        minHTemp = MIN_HEAT_TEMP_C // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
                        maxHTemp = MAX_HEAT_TEMP_C // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
                        log.trace "Location is in Celsius, MaxHeatTemp $maxHTemp, MinHeatTemp $minHTemp, MaxCoolTemp $maxCTemp, MinCoolTemp $minCTemp for thermostat"
                    } else {
                        minCTemp = MIN_COOL_TEMP_F // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
                        maxCTemp = MAX_COOL_TEMP_F // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
                        minHTemp = MIN_HEAT_TEMP_F // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
                        maxHTemp = MAX_HEAT_TEMP_F // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
                        log.trace "Location is in Farenheit, MaxHeatTemp $maxHTemp, MinHeatTemp $minHTemp, MaxCoolTemp $maxCTemp, MinCoolTemp $minCTemp for thermostat"
                    }

                    // Check if the see temperature is any of the set heat/cold or mix/min settings, otherwise it's a manual adjustment and we should be in temporary hold mode
                    if ((evt.name == "coolingSetpoint" ? [coolingSetpoint, minCTemp, maxCTemp] : [heatingSetpoint, minHTemp, maxHTemp]).any { it == (evt.value as Integer)}) {
                        log.trace "Found ${remoteTemperatureSensor} remote temperature sensor connected to thermostat ${evt.device.displayName} with predefined setpoint ${evt.value} for ${evt.name} in mode ${modeName}, disabling temporary hold"
                        atomicState."holdTemp${evt.device}" = false
                    } else {
                        log.info "Found ${remoteTemperatureSensor} remote temperature sensor connected to thermostat ${evt.device.displayName} with manual setpoint ${evt.value} for ${evt.name} in mode ${modeName}, enabling temporary hold"
                        atomicState."holdTemp${evt.device}" = true
                    }
                }
            }
        }
    }
}

// Handle remote temp sensor, set temperature if using a remote sensor
def remoteChangeHandler(evt) {
    log.debug "Reinitializing thermostat on remote sensor ${evt.device.displayName} temp change notification, name: ${evt.name}, value: ${evt.value}, mode: ${location.mode}"

	// Since we are manually entering the mode, check the mode before continuing since these aren't registered with the system (thanks @bravenel)
    if (modes && !modes.contains(location.mode)) {
    	log.debug "${location.mode} mode not in list of selected modes $modes"
    	return
    }
    
    // Lets find which thermostats are linked to this sensor and then update thermostat settings accordingly
    def maxModes = multiTempModes ? (modes == null ? 1 : modes.size()) : 1
    for (int j = 0; j < maxModes; j++) {
        if (modes && !modes[j]?.contains(location.mode)) {
            continue // this isn't our mode, ignore it
        }
        def maxThermostats = multiTempThermostat ? thermostats.size() : 1
        for (int i = 0; i < maxThermostats; i++) {
            if(settings."remoteTemperatureSensor${i}${j}"*.displayName?.contains(evt.device.displayName)) {
                if (atomicState."holdTemp${thermostats[i]}") { // If we are on hold temp mode then ignore temperature changes since user has put it on hold mode
                    log.trace "Thermostat ${thermostats[i]} is in hold temperature mode, not making any changes to thermostat based on remote temp sensor"
                } else {
                    setActiveTemperature(i, j)
                }
            }
        }
    }
}

// Set the active thermostat temperature on thermostat
private setActiveTemperature(i, j)
{
    def coolingSetpoint = settings."opCoolSet${i}${j}"
    def heatingSetpoint = settings."opHeatSet${i}${j}"
    def thermostat = thermostats[i]
    def thermostatState = thermostat.latestValue("thermostatMode")
    def thermostatCurrentHeating = thermostat.currentValue("heatingSetpoint")
    def thermostatCurrentCooling = thermostat.currentValue("coolingSetpoint")
    def threshold = settings."threshold${i}${j}"
    def remoteTemperatureSensor = settings."remoteTemperatureSensor${i}${j}"

    if (!thermostat) {
        log.error "No thermostat selected, not doing anything"
        return
    }
    
    log.trace "Thermostat ${thermostat.displayName} mode: $thermostatState, Target Heat: $heatingSetpoint°, Target Cool: $coolingSetpoint°"

    // Check for invalid configuration
    if ((thermostatState == "auto") && (heatingSetpoint > coolingSetpoint)) {
        log.error "INVALID CONFIGURATION: Target Heat temperature: $heatingSetpoint° is GREATER than Target Cool temperature: $coolingSetpoint°"
        log.error "Not changing temperature settings on thermostat, correct the SmartApp settings"
        return
    }

    if (remoteTemperatureSensor) { // Remote temperature sensor
        def locationScale = getTemperatureScale()
        def maxCTemp
        def minCTemp
        def maxHTemp
        def minHTemp
        if (locationScale == "C") {
            minCTemp = MIN_COOL_TEMP_C // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
            maxCTemp = MAX_COOL_TEMP_C // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
            minHTemp = MIN_HEAT_TEMP_C // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
            maxHTemp = MAX_HEAT_TEMP_C // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
            log.trace "Location is in Celsius, MaxHeatTemp $maxHTemp, MinHeatTemp $minHTemp, MaxCoolTemp $maxCTemp, MinCoolTemp $minCTemp for thermostat"
        } else {
            minCTemp = MIN_COOL_TEMP_F // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
            maxCTemp = MAX_COOL_TEMP_F // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
            minHTemp = MIN_HEAT_TEMP_F // minimum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to minimum)
            maxHTemp = MAX_HEAT_TEMP_F // maximum temperature (thermostat could be in a different room at different temp or not updated very often so inaccurate hence to set to maximum)
            log.trace "Location is in Farenheit, MaxHeatTemp $maxHTemp, MinHeatTemp $minHTemp, MaxCoolTemp $maxCTemp, MinCoolTemp $minCTemp for thermostat"
        }

        def currentTemp = (remoteTemperatureSensor*.currentTemperature).sum()/(remoteTemperatureSensor*.currentTemperature).count { it } // Take the average temp of the remote temperature sensor(s) (manage transition from legacy code to new code)
        log.trace("Remote Sensor Current Temp: $currentTemp°, Swing Threshold: $threshold")

        if (thermostatState == "auto") {
            // Cooling first
            if ((currentTemp - coolingSetpoint) > threshold) { // Turn cool on
                if (thermostatCurrentCooling != minCTemp) {
                    thermostat.setCoolingSetpoint(minCTemp) // Set to cool
                }
                if (thermostatCurrentHeating != minHTemp) {
                    thermostat.setHeatingSetpoint(minHTemp) // Disable heat
                }
                log.info "Cooling ON, Thermostat Cool: ${minCTemp}, Target: $coolingSetpoint°"
            } else if ((heatingSetpoint - currentTemp) > threshold) { // Heating second (order is important to avoid constant switching)
                if (thermostatCurrentHeating != maxHTemp) {
                    thermostat.setHeatingSetpoint(maxHTemp) // Set to heat
                }
                if (thermostatCurrentCooling != maxCTemp) {
                    thermostat.setCoolingSetpoint(maxCTemp) // Disable cool
                }
                log.info "Heating ON, Thermostat Heat: ${maxHTemp}, Target: $heatingSetpoint°"
            } else if (((coolingSetpoint - currentTemp) > threshold) || ((currentTemp - heatingSetpoint) > threshold)) { // Turn off - don't check valid mode
                if (thermostatCurrentCooling != maxCTemp) {
                    thermostat.setCoolingSetpoint(maxCTemp) // Disable cool
                }
                if (thermostatCurrentHeating != minHTemp) {
                    thermostat.setHeatingSetpoint(minHTemp) // Disable heat
                }
                log.info "HVAC OFF, Thermostat Cool: ${maxCTemp}, Thermostat Heat: ${minHTemp}"
            }
        } else if (thermostatState == "cool") {
            // air conditioner
            if ((currentTemp - coolingSetpoint) > threshold) { // Turn cool on
                if (thermostatCurrentCooling != minCTemp) {
                    thermostat.setCoolingSetpoint(minCTemp) // Set to cool
                }
                log.info "Cooling ON, Thermostat: ${minCTemp}, Target: $coolingSetpoint°"
            } else if ((coolingSetpoint - currentTemp) > threshold) { // Turn cool off - don't check valid mode
                if (thermostatCurrentCooling != maxCTemp) {
                    thermostat.setCoolingSetpoint(maxCTemp) // Disable cool
                }
                log.info "Cooling OFF, Thermostat Cool: ${maxCTemp}"
            }
        } else {
            // Heater or emergency heater
            if ((heatingSetpoint - currentTemp) > threshold) {
                if (thermostatCurrentHeating != maxHTemp) {
                    thermostat.setHeatingSetpoint(maxHTemp) // Set to heat
                }
                log.info "Heating ON, Thermostat: ${maxHTemp}, Target: $heatingSetpoint°"
            } else if ((currentTemp - heatingSetpoint) > threshold) { // Disable heat - don't check valid mode
                if (thermostatCurrentHeating != minHTemp) {
                    thermostat.setHeatingSetpoint(minHTemp) // Disable heat
                }
                log.info "Heating OFF, Thermostat Heat: ${minHTemp}"
            }
        }
    } else { // Local thermostat
        if (thermostatState == "auto") {
            thermostat.setHeatingSetpoint(heatingSetpoint)
            thermostat.setCoolingSetpoint(coolingSetpoint)
            log.info "Set $thermostat Heat ${heatingSetpoint}°, Cool ${coolingSetpoint}°"
        } else if (thermostatState == "cool") {
            thermostat.setCoolingSetpoint(coolingSetpoint)
            log.info "Set $thermostat Cool ${coolingSetpoint}°"
        } else { // heater or emergency heater
            thermostat.setHeatingSetpoint(heatingSetpoint)
            log.info "Set $thermostat Heat ${heatingSetpoint}°"
        }
    }
}

// Handle mode changes, reinitialize the current temperature after a mode change
def modeChangeHandler(evt) {
	// Since we are manually entering the mode, check the mode before continuing since these aren't registered with the system (thanks @bravenel)
    if (modes && !modes.contains(evt.value)) {
    	log.debug "$evt.value mode not in list of selected modes $modes"
    	return
    }
    
    thermostats?.each { atomicState."holdTemp${it}" = false } // Reset temporary hold mode
    
    def maxModes = multiTempModes ? (modes == null ? 1 : modes.size()) : 1
    int j = 0
    for (j = 0; j < maxModes; j++) {
    	def modeName = multiTempModes ? (modes == null ? "All" : modes[j]) : "All"
        if ((modeName == evt.value) || (modeName == "All")) { // check for matching mode in loop
            break // got it
        }
    }

    def maxThermostats = multiTempThermostat ? thermostats.size() : 1
    for (int i = 0; i < maxThermostats; i++) {
        setActiveTemperature(i, j)
        sendNotificationEvent("Set ${thermostats[i]} Heat $opHeatSet°, Cool $opCoolSet° on $evt.value mode")
    }
}

def checkForCodeUpdate(evt) {
    log.trace "Getting latest version data from the RBoy Apps server"
    
    def appName = "Ultimate Mode Change Thermostat Temperature"
    def serverUrl = "http://smartthings.rboyapps.com"
    def serverPath = "/CodeVersions.json"
    
    try {
        httpGet([
            uri: serverUrl,
            path: serverPath
        ]) { ret ->
            log.trace "Received response from RBoy Apps Server, headers=${ret.headers.'Content-Type'}, status=$ret.status"
            //ret.headers.each {
            //    log.trace "${it.name} : ${it.value}"
            //}

            if (ret.data) {
                log.trace "Response>" + ret.data
                
                // Check for app version updates
                def appVersion = ret.data?."$appName"
                if (appVersion > clientVersion()) {
                    def msg = "New version of app ${app.label} available: $appVersion, current version: ${clientVersion()}.\nPlease visit $serverUrl to get the latest version."
                    log.info msg
                    if (!disableUpdateNotifications) {
                        sendPush(msg)
                    }
                } else {
                    log.trace "No new app version found, latest version: $appVersion"
                }
                
                // Check device handler version updates
                def caps = [ thermostats ]
                caps?.each {
                    def devices = it?.findAll { it.hasAttribute("codeVersion") }
                    for (device in devices) {
                        if (device) {
                            def deviceName = device?.currentValue("dhName")
                            def deviceVersion = ret.data?."$deviceName"
                            if (deviceVersion && (deviceVersion > device?.currentValue("codeVersion"))) {
                                def msg = "New version of device ${device?.displayName} available: $deviceVersion, current version: ${device?.currentValue("codeVersion")}.\nPlease visit $serverUrl to get the latest version."
                                log.info msg
                                if (!disableUpdateNotifications) {
                                    sendPush(msg)
                                }
                            } else {
                                log.trace "No new device version found for $deviceName, latest version: $deviceVersion, current version: ${device?.currentValue("codeVersion")}"
                            }
                        }
                    }
                }
            } else {
                log.error "No response to query"
            }
        }
    } catch (e) {
        log.error "Exception while querying latest app version: $e"
    }
}