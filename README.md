# QUEMSI — Data management for Developers
Databases are most fundemental component of many software, Quemsi is a simple tool to master data in your databases. 
Quemsi enables you to take snapshots of your data with meaningful tags, search among them, and restore it to same or another database.
Your data always stays at your control and in your network through usage of distributed agent architecture.
You can work in your local environments with the comfort of web ui provided as a SaaS platform.
This opens many possiblity :
- Customization of how your database is backed up
- Regular backup your databases
- Moving back to any previous state easily
- Moving data between different enviroments (local, test, uat, prod ...)
for more information and start using visit [quemsi.com](https://quemsi.com)

# QUEMSI Agent
Quemsi agent is an opensource lightweight agent which performs all data operation at your local environment. It only recieves metadata from [quemsi.com](https://quemsi.com) and report status back. You can examine the interface from the [shared interface](https://github.com/quemsi/quemsi-model) repository. 

This repository allows you to fully investigate and understand what will be running on your side. All binary artifacts (jar, exe, linux binary) is produced from this code base. You can build yourself as any Spring boot and GraalVM project. 

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.1.5/maven-plugin/reference/html/)
* [Create native binary](https://www.graalvm.org/22.2/reference-manual/native-image/guides/build-spring-boot-app-into-native-executable/)

