description = "OrderFlow order-service"

dependencies {
    add("implementation", "org.springframework.boot:spring-boot-starter-kafka")
    add("implementation", "org.springframework.boot:spring-boot-starter-restclient")
    add("implementation", "io.jsonwebtoken:jjwt-api:0.13.0")
    add("runtimeOnly", "io.jsonwebtoken:jjwt-impl:0.13.0")
    add("runtimeOnly", "io.jsonwebtoken:jjwt-jackson:0.13.0")
    add("testImplementation", "org.testcontainers:testcontainers-kafka")
}
