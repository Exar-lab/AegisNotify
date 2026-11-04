"""On-demand AWS demo for AegisNotify (issue #238): provisions the EC2 host for docs/deploy-vm.md.

This script only creates and guards the machine. The stack itself (docker-compose.prod.yml, Caddy,
Keycloak realm, `scripts/deploy`) is the one from issue #236; the instance runs it on first boot.

Actions: plan | up | start | stop | status | destroy

    python scripts/aws-demo/aws_demo.py plan
    python scripts/aws-demo/aws_demo.py up --frontend-origin https://my-demo.vercel.app --acme-email you@example.com
    python scripts/aws-demo/aws_demo.py stop          # stop paying for compute
    python scripts/aws-demo/aws_demo.py start
    python scripts/aws-demo/aws_demo.py destroy --yes # deletes the instance, its disk and all data

Everything is created in REGION, tagged Project=aegisnotify-demo, and found again by that tag, so
every action is idempotent and `destroy` removes exactly what `up` created. Secrets are generated
by `scripts/deploy init` on the instance (.env.prod, mode 600) and are never printed here.

`main(call, ...)` takes an async `call(service, operation, region, params)`, so the same code runs
locally through boto3 (see `boto3_call`) or inside the AWS MCP `run_script` sandbox.
"""

import argparse
import asyncio
import json
import re

REGION = "us-east-2"  # the only region the account's organization policy allows EC2 in
PREFIX = "aegis-demo"
PROJECT_TAG = {"Key": "Project", "Value": "aegisnotify-demo"}
INSTANCE_TYPE = "t3.large"  # 8 GiB, the size docker-compose.prod.yml memory limits are written for
VOLUME_GB = 30
REPO = "Exar-lab/AegisNotify"
AMI_PARAM = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
IDLE_CPU_PERCENT = 10  # alarm stops the instance after 1 h below this average CPU
NIGHTLY_STOP_CRON = "cron(0 4 * * ? *)"  # 04:00 UTC, backstop if the alarm never fires

NAMES = {
    "role": f"{PREFIX}-ec2",
    "profile": f"{PREFIX}-ec2",
    "sched_role": f"{PREFIX}-scheduler",
    "sg": f"{PREFIX}-sg",
    "alarm": f"{PREFIX}-idle-stop",
    "schedule": f"{PREFIX}-nightly-stop",
}


async def boto3_call(service, operation, region=None, params=None):
    """Local runner: PascalCase operation -> boto3 client method (run in a thread)."""
    import boto3

    method = re.sub(r"(?<!^)(?=[A-Z])", "_", operation).lower()
    client = boto3.client(service, region_name=region)
    return await asyncio.to_thread(getattr(client, method), **(params or {}))


def is_missing(error):
    text = str(error)
    return any(m in text for m in ("NoSuchEntity", "NotFound", "ResourceNotFound", "does not exist",
                                   "InvalidGroup.NotFound", "ParameterNotFound"))


class Demo:
    def __init__(self, call, frontend_origin=None, acme_email=None, repo_ref="main", instance_type=INSTANCE_TYPE):
        self.call = call
        self.frontend_origin = frontend_origin
        self.acme_email = acme_email
        self.repo_ref = repo_ref
        self.instance_type = instance_type

    async def ec2(self, op, **params):
        return await self.call("ec2", op, REGION, params)

    # ---------------------------------------------------------------- discovery
    async def instance(self):
        out = await self.ec2("DescribeInstances", Filters=[
            {"Name": "tag:Project", "Values": [PROJECT_TAG["Value"]]},
            {"Name": "instance-state-name", "Values": ["pending", "running", "stopping", "stopped"]}])
        found = [i for r in out["Reservations"] for i in r["Instances"]]
        return found[0] if found else None

    async def address(self):
        out = await self.ec2("DescribeAddresses", Filters=[{"Name": "tag:Project", "Values": [PROJECT_TAG["Value"]]}])
        return out["Addresses"][0] if out["Addresses"] else None

    async def security_group(self):
        out = await self.ec2("DescribeSecurityGroups", Filters=[{"Name": "group-name", "Values": [NAMES["sg"]]}])
        return out["SecurityGroups"][0] if out["SecurityGroups"] else None

    async def exists(self, service, op, region=None, **params):
        try:
            await self.call(service, op, region, params)
            return True
        except Exception as error:
            if is_missing(error):
                return False
            raise

    async def hosts(self, address=None):
        address = address or await self.address()
        if not address:
            return None
        dashed = address["PublicIp"].replace(".", "-")
        return {"public": f"{dashed}.sslip.io", "api": f"api.{dashed}.sslip.io", "auth": f"auth.{dashed}.sslip.io"}

    # ---------------------------------------------------------------- plan / status
    async def plan(self):
        inst, addr, sg = await self.instance(), await self.address(), await self.security_group()
        checks = {
            "IAM role (EC2)": await self.exists("iam", "GetRole", None, RoleName=NAMES["role"]),
            "IAM instance profile": await self.exists("iam", "GetInstanceProfile", None, InstanceProfileName=NAMES["profile"]),
            "IAM role (scheduler)": await self.exists("iam", "GetRole", None, RoleName=NAMES["sched_role"]),
            "security group": sg is not None,
            "elastic IP": addr is not None,
            "EC2 instance": inst is not None,
            "idle-stop alarm": bool((await self.call("cloudwatch", "DescribeAlarms", REGION, {"AlarmNames": [NAMES["alarm"]]}))["MetricAlarms"]),
            "nightly-stop schedule": await self.exists("scheduler", "GetSchedule", REGION, Name=NAMES["schedule"]),
        }
        steps = [f"{'exists ' if present else 'CREATE '} {name}" for name, present in checks.items()]
        return {"region": REGION, "instance_type": INSTANCE_TYPE, "steps": steps,
                "needs": "--frontend-origin and --acme-email" if not all(checks.values()) else None}

    async def status(self):
        inst, addr = await self.instance(), await self.address()
        hosts = await self.hosts(addr)
        return {
            "instance": None if not inst else {"id": inst["InstanceId"], "state": inst["State"]["Name"], "type": inst["InstanceType"]},
            "elastic_ip": addr["PublicIp"] if addr else None,
            "api_url": f"https://{hosts['api']}" if hosts else None,
            "auth_url": f"https://{hosts['auth']}" if hosts else None,
            "note": "First boot builds the images and takes roughly 15-30 minutes; after a later start allow about 5. Demo login and secrets live in /opt/aegisnotify/.env.prod on the instance (read it through SSM Session Manager).",
        }

    # ---------------------------------------------------------------- up
    async def ensure_iam(self):
        trust = lambda svc: json.dumps({"Version": "2012-10-17", "Statement": [
            {"Effect": "Allow", "Principal": {"Service": svc}, "Action": "sts:AssumeRole"}]})
        tags = [PROJECT_TAG]
        if not await self.exists("iam", "GetRole", None, RoleName=NAMES["role"]):
            await self.call("iam", "CreateRole", None, {"RoleName": NAMES["role"], "Tags": tags,
                            "AssumeRolePolicyDocument": trust("ec2.amazonaws.com")})
            await self.call("iam", "AttachRolePolicy", None, {"RoleName": NAMES["role"],
                            "PolicyArn": "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"})
        if not await self.exists("iam", "GetInstanceProfile", None, InstanceProfileName=NAMES["profile"]):
            await self.call("iam", "CreateInstanceProfile", None, {"InstanceProfileName": NAMES["profile"], "Tags": tags})
            await self.call("iam", "AddRoleToInstanceProfile", None, {"InstanceProfileName": NAMES["profile"], "RoleName": NAMES["role"]})
        if not await self.exists("iam", "GetRole", None, RoleName=NAMES["sched_role"]):
            await self.call("iam", "CreateRole", None, {"RoleName": NAMES["sched_role"], "Tags": tags,
                            "AssumeRolePolicyDocument": trust("scheduler.amazonaws.com")})
            await self.call("iam", "PutRolePolicy", None, {"RoleName": NAMES["sched_role"], "PolicyName": "stop-demo-instance",
                            "PolicyDocument": json.dumps({"Version": "2012-10-17", "Statement": [{
                                "Effect": "Allow", "Action": "ec2:StopInstances", "Resource": f"arn:aws:ec2:{REGION}:*:instance/*",
                                "Condition": {"StringEquals": {"ec2:ResourceTag/Project": PROJECT_TAG["Value"]}}}]})})

    def user_data(self, public_ip):
        """First-boot script: Docker, swap, the repository, then the issue #236 stack."""
        return f"""#!/bin/bash
set -euxo pipefail
install -m 600 /dev/null /var/log/aegis-bootstrap.log
exec >> /var/log/aegis-bootstrap.log 2>&1
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
git clone --branch {self.repo_ref} https://github.com/{REPO}.git /opt/aegisnotify
cd /opt/aegisnotify
bash scripts/deploy init {public_ip} {self.acme_email} {self.frontend_origin.rstrip("/")}
bash scripts/deploy up
"""

    async def up(self):
        inst = await self.instance()
        if not inst and not (self.frontend_origin and self.acme_email):
            raise SystemExit("up needs --frontend-origin (your Vercel URL) and --acme-email (certificate expiry notices)")
        await self.ensure_iam()
        sg = await self.security_group()
        if not sg:
            vpc = (await self.ec2("DescribeVpcs", Filters=[{"Name": "is-default", "Values": ["true"]}]))["Vpcs"][0]["VpcId"]
            group = await self.ec2("CreateSecurityGroup", GroupName=NAMES["sg"], VpcId=vpc, Description="AegisNotify demo: HTTP/HTTPS only",
                                   TagSpecifications=[{"ResourceType": "security-group", "Tags": [PROJECT_TAG]}])
            await self.ec2("AuthorizeSecurityGroupIngress", GroupId=group["GroupId"], IpPermissions=[
                {"IpProtocol": "tcp", "FromPort": port, "ToPort": port, "IpRanges": [{"CidrIp": "0.0.0.0/0"}]} for port in (80, 443)])
            sg = {"GroupId": group["GroupId"]}
        addr = await self.address()
        if not addr:
            new = await self.ec2("AllocateAddress", Domain="vpc",
                                 TagSpecifications=[{"ResourceType": "elastic-ip", "Tags": [PROJECT_TAG]}])
            addr = {"AllocationId": new["AllocationId"], "PublicIp": new["PublicIp"]}
        if not inst:
            ami = (await self.call("ssm", "GetParameter", REGION, {"Name": AMI_PARAM}))["Parameter"]["Value"]
            for attempt in range(8):  # a new instance profile can take a few seconds to propagate
                try:
                    run = await self.ec2(
                        "RunInstances", ImageId=ami, InstanceType=self.instance_type, MinCount=1, MaxCount=1,
                        IamInstanceProfile={"Name": NAMES["profile"]}, SecurityGroupIds=[sg["GroupId"]],
                        # `unlimited` lets the first image builds burst; idle use stays within the baseline.
                        CreditSpecification={"CpuCredits": "unlimited"},
                        MetadataOptions={"HttpTokens": "required", "HttpPutResponseHopLimit": 2},
                        BlockDeviceMappings=[{"DeviceName": "/dev/sda1", "Ebs": {
                            "VolumeSize": VOLUME_GB, "VolumeType": "gp3", "Encrypted": True, "DeleteOnTermination": True}}],
                        UserData=self.user_data(addr["PublicIp"]),
                        TagSpecifications=[{"ResourceType": rt, "Tags": [PROJECT_TAG, {"Key": "Name", "Value": PREFIX}]}
                                           for rt in ("instance", "volume")])
                    inst = run["Instances"][0]
                    break
                except Exception as error:
                    if "Invalid IAM Instance Profile" not in str(error) or attempt == 7:
                        raise
                    await asyncio.sleep(5)
        for attempt in range(30):  # the address can only be attached once the instance is running
            try:
                await self.ec2("AssociateAddress", AllocationId=addr["AllocationId"], InstanceId=inst["InstanceId"], AllowReassociation=True)
                break
            except Exception as error:
                if "IncorrectInstanceState" not in str(error) or attempt == 29:
                    raise
                await asyncio.sleep(5)
        await self.ensure_guardrails(inst["InstanceId"])
        return await self.status()

    async def ensure_guardrails(self, instance_id):
        await self.call("cloudwatch", "PutMetricAlarm", REGION, {
            "AlarmName": NAMES["alarm"], "AlarmDescription": "Stops the AegisNotify demo when it has been idle for 1 hour.",
            "Namespace": "AWS/EC2", "MetricName": "CPUUtilization", "Statistic": "Average", "Period": 300,
            "EvaluationPeriods": 12, "DatapointsToAlarm": 12, "Threshold": IDLE_CPU_PERCENT,
            "ComparisonOperator": "LessThanThreshold", "TreatMissingData": "notBreaching",
            "Dimensions": [{"Name": "InstanceId", "Value": instance_id}],
            "AlarmActions": [f"arn:aws:automate:{REGION}:ec2:stop"], "Tags": [PROJECT_TAG]})
        role = await self.call("iam", "GetRole", None, {"RoleName": NAMES["sched_role"]})
        body = {"Name": NAMES["schedule"], "ScheduleExpression": NIGHTLY_STOP_CRON, "ScheduleExpressionTimezone": "UTC",
                "FlexibleTimeWindow": {"Mode": "OFF"},
                "Target": {"Arn": "arn:aws:scheduler:::aws-sdk:ec2:stopInstances", "RoleArn": role["Role"]["Arn"],
                           "Input": json.dumps({"InstanceIds": [instance_id]})}}
        if await self.exists("scheduler", "GetSchedule", REGION, Name=NAMES["schedule"]):
            await self.call("scheduler", "UpdateSchedule", REGION, body)
        else:
            for attempt in range(6):  # the scheduler role can take a few seconds to propagate
                try:
                    await self.call("scheduler", "CreateSchedule", REGION, body)
                    break
                except Exception as error:
                    if "assume" not in str(error).lower() or attempt == 5:
                        raise
                    await asyncio.sleep(5)

    # ---------------------------------------------------------------- start / stop / destroy
    async def power(self, action):
        inst = await self.instance()
        if not inst:
            raise SystemExit("No demo instance. Run `up` first.")
        await self.ec2("StartInstances" if action == "start" else "StopInstances", InstanceIds=[inst["InstanceId"]])
        return await self.status()

    async def destroy(self):
        removed = []
        async def attempt(label, service, op, region, **params):
            try:
                await self.call(service, op, region, params)
                removed.append(label)
            except Exception as error:
                if not is_missing(error):
                    removed.append(f"FAILED {label}: {str(error)[:120]}")
        await attempt("schedule", "scheduler", "DeleteSchedule", REGION, Name=NAMES["schedule"])
        await attempt("alarm", "cloudwatch", "DeleteAlarms", REGION, AlarmNames=[NAMES["alarm"]])
        inst = await self.instance()
        if inst:
            await self.ec2("TerminateInstances", InstanceIds=[inst["InstanceId"]])
            for _ in range(40):
                state = (await self.ec2("DescribeInstances", InstanceIds=[inst["InstanceId"]]))["Reservations"][0]["Instances"][0]["State"]["Name"]
                if state == "terminated":
                    break
                await asyncio.sleep(5)
            removed.append("instance (and its disk)")
        addr = await self.address()
        if addr:
            await attempt("elastic IP", "ec2", "ReleaseAddress", REGION, AllocationId=addr["AllocationId"])
        sg = await self.security_group()
        if sg:
            await attempt("security group", "ec2", "DeleteSecurityGroup", REGION, GroupId=sg["GroupId"])
        await attempt("EC2 role policy attachment", "iam", "DetachRolePolicy", None, RoleName=NAMES["role"],
                      PolicyArn="arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore")
        await attempt("profile role", "iam", "RemoveRoleFromInstanceProfile", None, InstanceProfileName=NAMES["profile"], RoleName=NAMES["role"])
        await attempt("instance profile", "iam", "DeleteInstanceProfile", None, InstanceProfileName=NAMES["profile"])
        await attempt("EC2 role", "iam", "DeleteRole", None, RoleName=NAMES["role"])
        await attempt("scheduler role policy", "iam", "DeleteRolePolicy", None, RoleName=NAMES["sched_role"], PolicyName="stop-demo-instance")
        await attempt("scheduler role", "iam", "DeleteRole", None, RoleName=NAMES["sched_role"])
        return {"removed": removed, "kept": "AWS Budget aegis-demo-credits (created separately)"}


async def main(call, action, frontend_origin=None, acme_email=None, repo_ref="main", instance_type=INSTANCE_TYPE, yes=False):
    demo = Demo(call, frontend_origin, acme_email, repo_ref, instance_type)
    if action == "plan":
        return await demo.plan()
    if action == "status":
        return await demo.status()
    if action == "up":
        return await demo.up()
    if action in ("start", "stop"):
        return await demo.power(action)
    if action == "destroy":
        if not yes:
            raise SystemExit("destroy deletes the instance, its disk and all demo data. Re-run with --yes.")
        return await demo.destroy()
    raise SystemExit(f"unknown action {action}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["plan", "up", "start", "stop", "status", "destroy"])
    parser.add_argument("--frontend-origin", help="Vercel origin allowed by CORS and Keycloak, e.g. https://my-demo.vercel.app")
    parser.add_argument("--repo-ref", default="main", help="Git ref of the repository to deploy from")
    parser.add_argument("--acme-email", help="Address for Let's Encrypt/ZeroSSL certificate expiry notices")
    parser.add_argument("--instance-type", default=INSTANCE_TYPE)
    parser.add_argument("--yes", action="store_true", help="Confirm destroy")
    args = parser.parse_args()
    print(json.dumps(asyncio.run(main(boto3_call, args.action, args.frontend_origin, args.acme_email, args.repo_ref, args.instance_type, args.yes)), indent=2))
