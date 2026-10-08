terraform {
  required_version = ">= 1.0.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "aws" {
  region      = "us-east-1"
  }

variable "nBroker" {
  description = "number of brokers"
  type        = number
  default     = 3
}

resource "aws_instance" "KafkaBroker" {
  ami                     = "ami-07ff62358b87c7116"
  instance_type           = "t3.small"
  count                   = var.nBroker
  vpc_security_group_ids  = [aws_security_group.instance.id]
  key_name                = "vockey"

  user_data = base64encode(templatefile("${path.module}/creation.sh", {
    idBroker      = count.index
    totalBrokers  = var.nBroker
    cluster_id    = random_uuid.kafka_cluster_id.result
  }))

  user_data_replace_on_change = true
  
  tags = {
    Name = "kafka.${count.index}"
  }
}

resource "random_uuid" "kafka_cluster_id" {}


locals {
  replica_directorys = [
    "r1eMpKZRROex80kgn4_2-g",
    "bWIe6tPFS3mKvq-W-_kgtQ",
    "Z57HkCAASdifFa-QWCdGWA"
  ]
  quorum_voters = join(",", [
    for i, instance in aws_instance.KafkaBroker :
    "${i + 1}@${instance.public_dns}:9093"
  ])
  quorum_voters_old_format = join(",", [
    for i, instance in aws_instance.KafkaBroker :
    "${i + 1}@${instance.public_dns}:9093:${local.replica_directorys[i]}"
  ])
}

resource "null_resource" "update_dns" {
  count = var.nBroker

  connection {
    type        = "ssh"
    user        = "ec2-user"
    private_key = file("key.pem")
    host        = aws_instance.KafkaBroker[count.index].public_ip
  }

  provisioner "remote-exec" {
    inline = [
      "echo -n '${local.quorum_voters}' > /home/ec2-user/cluster_hosts",
      "echo -n '${local.quorum_voters_old_format}' > /home/ec2-user/cluster_hosts_old_format",
      "cat /home/ec2-user/cluster_hosts",
      "cat /home/ec2-user/cluster_hosts_old_format"
    ]
  }
}

output "publicdnslist" {
  value = "${formatlist("%v", aws_instance.KafkaBroker.*.public_dns)}"
}

resource "aws_security_group" "instance" {
  name = var.security_group_name
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
    ingress {
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
    ingress {
      from_port   = 9093
      to_port     = 9093
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
    }
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

variable "security_group_name" {
  description = "The name of the security group"
  type        = string
  default     = "terraform-example-cluster"
}


