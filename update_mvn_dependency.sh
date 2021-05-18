for i in json-20200518 nio_char ojdbc8 ucp osdt_core osdt_cert oraclepki javax.json-1.0.4 json-simple-1.1 orajsoda-1.1.4 org.apache.commons.io xmlparserv2; do echo  /usr/local/apache-maven/bin/mvn -X install:install-file " -DgroupId=com.oracle.jdbc" "-DartifactId=$i" "-Dversion=19.3.0" "-Dpackaging=jar" "-Dfile=soda-jar/$i.jar"; done;

