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
    return "02.03.00"
}
 
/**
 *  Mode Change Thermostat Temperature
 *
 * Copyright RBoy, redistribution of any changes or modified code is not allowed without permission
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
		author: "RBoy",
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
    dynamicPage(name: "setupApp", title: "Mode and Thermostat Selection v${clientVersion()}", install: false, uninstall: true, nextPage: "tempPage") {
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
                }
            }
        }
    }
}

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

    subscribe(location, modeChangeHandler)
    log.debug "Selected Modes -> $modes, settings -> $settings"
}

// Handle mode changes, reinitialize the current temperature after a mode change
def modeChangeHandler(evt) {
	// Since we are manually entering the mode, check the mode before continuing since these aren't registered with the system (thanks @bravenel)
    if (modes && !modes.contains(evt.value)) {
    	log.debug "$evt.value mode not in list of selected modes $modes"
    	return
    }
        
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
        def opHeatSet = settings."opHeatSet${i}${j}"
        def opCoolSet = settings."opCoolSet${i}${j}"

        if (multiTempThermostat) { // individual thermostat settings
            thermostats[i].setHeatingSetpoint(opHeatSet)
            thermostats[i].setCoolingSetpoint(opCoolSet)
            log.info "Set ${thermostats[i]} Heat $opHeatSet°, Cool $opCoolSet° on $evt.value mode"
            sendNotificationEvent("Set ${thermostats[i]} Heat $opHeatSet°, Cool $opCoolSet° on $evt.value mode")
        } else {
            thermostats.setHeatingSetpoint(opHeatSet)
            thermostats.setCoolingSetpoint(opCoolSet)
            log.info "Set ${thermostats} Heat $opHeatSet°, Cool $opCoolSet° on $evt.value mode $i $j"
            sendNotificationEvent("Set ${thermostats} Heat $opHeatSet°, Cool $opCoolSet° on $evt.value mode")
        }
    }
}

def checkForCodeUpdate(evt) {
    log.trace "Getting latest version data from the RBoy server"
    
    def appName = "Ultimate Mode Change Thermostat Temperature"
    def serverUrl = "http://smartthings.rboyapps.com"
    def serverPath = "/CodeVersions.json"
    
    try {
        httpGet([
            uri: serverUrl,
            path: serverPath
        ]) { ret ->
            log.trace "Received response from RBoyServer, headers=${ret.headers.'Content-Type'}, status=$ret.status"
            //ret.headers.each {
            //    log.trace "${it.name} : ${it.value}"
            //}

            if (ret.data) {
                log.trace "Response>" + ret.data
                
                // Check for app version updates
                def appVersion = ret.data?."$appName"
                if (appVersion > clientVersion()) {
                    def msg = "New version of app ${app.label} available: $appVersion, version: ${clientVersion()}.\nPlease visit $serverUrl to get the latest version."
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
                                def msg = "New version of device ${device?.displayName} available: $deviceVersion, version: ${device?.currentValue("codeVersion")}.\nPlease visit $serverUrl to get the latest version."
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