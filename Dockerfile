# ============================================================
# Stage 1: Compile Java sources
# ============================================================
FROM eclipse-temurin:18-jdk AS builder

WORKDIR /build

# Copy source code and libraries
COPY src/ src/
COPY WebContent/WEB-INF/lib/ lib/

# Tomcat 10 Jakarta Servlet API (needed for compilation)
ADD https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/5.0.0/jakarta.servlet-api-5.0.0.jar lib/jakarta.servlet-api-5.0.0.jar

# Compile all Java files
RUN mkdir -p classes && \
    find src -name '*.java' > sources.txt && \
    javac \
      -cp "lib/*" \
      -d classes \
      -source 18 \
      -target 18 \
      @sources.txt

# ============================================================
# Stage 2: Package into Tomcat
# ============================================================
FROM tomcat:10.0-jdk18-temurin

# Remove default Tomcat webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Create the ROOT webapp directory structure
RUN mkdir -p /usr/local/tomcat/webapps/ROOT/WEB-INF/classes \
             /usr/local/tomcat/webapps/ROOT/WEB-INF/lib

# Copy web content (JSP, CSS, JS, web.xml)
COPY WebContent/ /usr/local/tomcat/webapps/ROOT/

# Copy compiled classes from builder stage
COPY --from=builder /build/classes/ /usr/local/tomcat/webapps/ROOT/WEB-INF/classes/

# Copy runtime libraries (MariaDB driver, json-simple)
COPY WebContent/WEB-INF/lib/ /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/

EXPOSE 8080

CMD ["catalina.sh", "run"]
