# GPS Cockpit 

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="250"> 

Show your GPS data in a cockpit view

<a href="https://f-droid.org/packages/org.woheller69.gpscockpit"><img alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="100"></a>

## Features

GPS Cockpit finds your device's location via GPS and shows most relevant data in a cockpit style.
You can also see the list of visible satellites with their identifiers and signal quality.
Location coordinates can be copied to clipboard, shared, or opened in a maps app, if installed.
Clearing A-GPS aiding data is also supported.

Speed range can be selected by clicking on the speedometer.
When the START button is pressed travel distance is recorded (horizontal movement and also
accumulated up and down movements). A change is only recognized and added to the distance if
the position changes by more than 2x GPS accuracy for horizontal movement and 3x GPS accuracy for
vertical movements and only if accuracy is at least 15m.

Depending on the device this may or may not work with screen off. 
Some manufacturers have implemented hostile energy management options and do not respect
app permissions for holding wake locks or ignore battery optimizations.

See https://dontkillmyapp.com/


## Third-Party Resources

* The program is based on https://github.com/mirfatif/MyLocation published under AGPL 3.0 or later
* https://github.com/androidx/androidx published under Apache License 2.0
* https://github.com/material-components/material-components-android published under Apache License 2.0
* https://github.com/sherter/google-java-format-gradle-plugin published under MIT License
* https://github.com/saket/Better-Link-Movement-Method published under Apache License 2.0
* https://github.com/anastr/SpeedView published under Apache License 2.0
* https://github.com/kix2902/CompassView published under Apache License 2.0
* https://github.com/barbeau/gpstest  (getAltitudeMeanSeaLevel from NMEA strings) published under Apache License 2.0

## License 

You **CANNOT** use and distribute the app icon in anyway, except for **GPS Cockpit** (`org.woheller69.gpscockpit`) app.

    GPS Cockpit is free software: you can redistribute it and/or modify
    it under the terms of the Affero GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    Affero GNU General Public License for more details.

    You should have received a copy of the Affero GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

