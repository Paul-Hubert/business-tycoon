DROP USER 'user'@'localhost';
CREATE USER 'user'@'localhost';
GRANT ALL PRIVILEGES ON db.* TO 'user'@'localhost' IDENTIFIED BY 'password';
FLUSH PRIVILEGES;
DROP DATABASE db;
CREATE DATABASE db CHARACTER SET utf8;
USE db;
CREATE TABLE users (
	id INT NOT NULL AUTO_INCREMENT,
	user VARCHAR(128) NOT NULL,
	pass VARCHAR(256) NOT NULL,
	PRIMARY KEY (id)
);
INSERT INTO users (user, pass) VALUES ("test", "test");