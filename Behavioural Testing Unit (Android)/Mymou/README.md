# Instructions for Adapting Code 

## Requirements

Java: 11
Gradle: 6.5

## Set up in git and android studio 
- Download android studio.
- Fork the Mymou project to your own git repository.
- Clone your git repository into a local folder. 
- In android studio click File | Open project | and select testing unit mymou folder: 
Mymou/Behavioural Testing Unit (Android)/Mymou/ 
- The building process should start automatically but will likely run into an error. 
This is most likely due to a the default java version being incompatible with the gradle or kotlin 
plugin versions. The easiest way to fix this is to downgrade to Java 11. If you instead try to 
upgrade the gradle version, you will run into issues down the road that can be hard to 
resolve as the code relies on functions and syntax that are no longer supported in newer gradle 
versions. 
- To downgrade to Java 11, open File | Settings | Build, Execution, Deployment | Build Tools | Gradle
- Click Gradle JDK and download java 11 (i.e., Coretto 11 / amazon coretto 11)
- When download complete, select Coretto 11 as your JDK from the drop down menu, and click Apply. 
- Now go to File and click Sync Project with Gradle File. This should build the app.

Now you can create and select a device from the Device Manager (right-hand menu) which the code will 
be emulated on when you press run. 

