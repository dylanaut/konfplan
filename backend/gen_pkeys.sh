#!/usr/bin/env bash
echo "Generate public and private keys for asymmetric JWT signing"

openssl genpkey -algorithm RSA -out privateKey.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in privateKey.pem -out publicKey.pem

echo "You should move *.pem to ./src/main/resources/ and configure application.properties accordingly"