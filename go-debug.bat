set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080

echo.
echo ########### CLEAN ###########
call mvn clean 

echo.
echo ########### INSTALL ###########
call mvn install -DskipTests=true 

echo.
echo ########### HPI:RUN ###########
call mvn hpi:run -Djetty.port=8090  -DskipTests=true 
