# swagger-maven-plugin-ninja-plugin

A [Ninja web framework](http://www.ninjaframework.org) plugin for [swagger-maven-plugin](https://github.com/kongchen/swagger-maven-plugin).

## Supporting versions

* Swagger Spec 2.0
* Ninja web framework 5.8 (not 6.x)
* Maven 3.x

## Usage

### pom.xml

```xml
<build>
    <plugins>
    	<plugin>
            <groupId>com.github.kongchen</groupId>
            <artifactId>swagger-maven-plugin</artifactId>
            <version>3.1.7</version>
            <configuration>
                <apiSources>
                    <apiSource>
                        <locations>
                            <location>controllers.api</location>
                        </locations>
                        <basePath>/</basePath>
                        <info>
                            <title>Your API</title>
                            <version>v1</version>
                            <description>
                                This is your API.
                            </description>
                        </info>
                        <swaggerDirectory>${basedir}/src/main/java/assets/swagger-ui</swaggerDirectory>

                        <!-- Important -->
                        <swaggerApiReader>com.github.mallowlabs.swagger.docgen.ninja.NinjaReader</swaggerApiReader>
                    </apiSource>
                </apiSources>
            </configuration>
            <executions>
                <execution>
                    <phase>compile</phase>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                </execution>
            </executions>
            <dependencies>
                <!-- Important -->
                <dependency>
                    <groupId>com.github.mallowlabs</groupId>
                    <artifactId>swagger-maven-plugin-ninja-plugin</artifactId>
                    <version>1.0-SNAPSHOT</version>
                </dependency>
            </dependencies>
        </plugin>
    </plugins>
</build>

```

### conf/Route.java

```java
router.GET().route("/api/version").with(APIController.class, "version");
```

### controllers/APIController.java

```java
package controllers.api;

import com.google.inject.Singleton;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import models.YourVersion;
import ninja.Result;
import ninja.Results;

@Singleton
@Api(value = "api")
public class APIController {

    @ApiOperation(value = "version", response = YourVersion.class, notes = "Get system versionn")
    public Result version() {
        YourVersion version = new YourVersion();
        version.setServerVersion("v1.0");

        Result result = Results.json();
        result.render(version);
        return result;
    }
}

```

### models/YourVersion.java

```java
package models;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "Version information")
public class YourVersion {

    @ApiModelProperty(value = "Server application version", example = "v1.0")
    public String serverVersion;
}
```

When you run `mvn compile`, you will get `swagger.json` at `/src/main/java/assets/swagger-ui/swagger.json`

## Credits

This project re-uses [ninja-swagger-maven-plugin](https://github.com/oakfusion/ninja-swagger-maven-plugin).
[Apache License 2.0](https://github.com/oakfusion/ninja-swagger-maven-plugin/blob/master/LICENSE) by oakfusion