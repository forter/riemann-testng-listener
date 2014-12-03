riemann-testng-listener
=======================

reports testng test results to riemann (use with maven-surefire-plugin or maven-failsafe-plugin)

pom.xml integration:
```
 <build>
   <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>2.15</version>
      <configuration>
          <properties>
            <property>
              <name>listener</name>
              <value>com.forter.monitoring.RiemannListener</value>
            </property>
        </properties>
      </configuration>
    </plugin>
  </plugins>
 </build>
 
 <dependencies>
    <dependency>
      <groupId>com.forter</groupId>
      <artifactId>riemann-testng-listener</artifactId>
      <version>0.2</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  
  <repositories>
    <repository>
      <id>forter-public</id>
      <name>forter public</name>
      <url>http://oss.forter.com/repository</url>
      <releases>
        <checksumPolicy>fail</checksumPolicy>
      </releases>
      <snapshots>
        <checksumPolicy>fail</checksumPolicy>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>
```
