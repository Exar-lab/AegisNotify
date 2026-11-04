"""On-demand AWS demo for AegisNotify (issue #238): one EC2 host running docker-compose.demo.yml.

Actions: plan | up | start | stop | status | destroy

    python scripts/aws-demo/aws_demo.py plan
    python scripts/aws-demo/aws_demo.py up --frontend-origin https://my-demo.vercel.app
    python scripts/aws-demo/aws_demo.py stop          # stop paying for compute
    python scripts/aws-demo/aws_demo.py start
    python scripts/aws-demo/aws_demo.py destroy --yes # deletes the instance, its disk and all data

Everything is created in REGION, tagged Project=aegisnotify-demo, and found again by that tag, so
every action is idempotent and `destroy` removes exactly what `up` created. Secrets are generated
on first `up`, stored as SSM SecureString parameters and never printed.

`main(call, ...)` takes an async `call(service, operation, region, params)`, so the same code runs
locally through boto3 (see `boto3_call`) or inside the AWS MCP `run_script` sandbox.
"""

import argparse
import asyncio
import base64
import json
import re
import uuid

REGION = "us-east-2"  # the only region the account's organization policy allows EC2 in
PREFIX = "aegis-demo"
PARAM_PATH = "/aegis-demo"
PROJECT_TAG = {"Key": "Project", "Value": "aegisnotify-demo"}
INSTANCE_TYPE = "t3.xlarge"
VOLUME_GB = 60
REPO = "Exar-lab/AegisNotify"
COMPOSE_VERSION = "v2.29.7"
AMI_PARAM = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
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
    def __init__(self, call, frontend_origin=None, repo_ref="main", version="0.18.0"):
        self.call = call
        self.frontend_origin = frontend_origin
        self.repo_ref = repo_ref
        self.version = version

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
        return {"api": f"api.{dashed}.sslip.io", "auth": f"auth.{dashed}.sslip.io"}

    # ---------------------------------------------------------------- plan / status
    async def plan(self):
        inst, addr, sg = await self.instance(), await self.address(), await self.security_group()
        checks = {
            "IAM role (EC2)": await self.exists("iam", "GetRole", None, RoleName=NAMES["role"]),
            "IAM instance profile": await self.exists("iam", "GetInstanceProfile", None, InstanceProfileName=NAMES["profile"]),
            "IAM role (scheduler)": await self.exists("iam", "GetRole", None, RoleName=NAMES["sched_role"]),
            "SSM parameters": bool((await self.call("ssm", "GetParametersByPath", REGION, {"Path": PARAM_PATH})).get("Parameters")),
            "security group": sg is not None,
            "elastic IP": addr is not None,
            "EC2 instance": inst is not None,
            "idle-stop alarm": bool((await self.call("cloudwatch", "DescribeAlarms", REGION, {"AlarmNames": [NAMES["alarm"]]}))["MetricAlarms"]),
            "nightly-stop schedule": await self.exists("scheduler", "GetSchedule", REGION, Name=NAMES["schedule"]),
        }
        steps = [f"{'exists ' if present else 'CREATE '} {name}" for name, present in checks.items()]
        return {"region": REGION, "instance_type": INSTANCE_TYPE, "steps": steps,
                "frontend_origin_needed": not all(checks.values()) and not self.frontend_origin}

    async def status(self):
        inst, addr = await self.instance(), await self.address()
        hosts = await self.hosts(addr)
        return {
            "instance": None if not inst else {"id": inst["InstanceId"], "state": inst["State"]["Name"], "type": inst["InstanceType"]},
            "elastic_ip": addr["PublicIp"] if addr else None,
            "api_url": f"https://{hosts['api']}" if hosts else None,
            "auth_url": f"https://{hosts['auth']}" if hosts else None,
            "note": "After start, wait about 4 minutes for Keycloak and the services to become healthy.",
        }

    # ---------------------------------------------------------------- up
    async def put_param(self, name, value, secure=True):
        await self.call("ssm", "PutParameter", REGION, {
            "Name": f"{PARAM_PATH}/{name}", "Value": value, "Type": "SecureString" if secure else "String",
            "Overwrite": True})

    async def ensure_iam(self):
        trust = lambda svc: json.dumps({"Version": "2012-10-17", "Statement": [
            {"Effect": "Allow", "Principal": {"Service": svc}, "Action": "sts:AssumeRole"}]})
        tags = [PROJECT_TAG]
        if not await self.exists("iam", "GetRole", None, RoleName=NAMES["role"]):
            await self.call("iam", "CreateRole", None, {"RoleName": NAMES["role"], "Tags": tags,
                            "AssumeRolePolicyDocument": trust("ec2.amazonaws.com")})
            await self.call("iam", "AttachRolePolicy", None, {"RoleName": NAMES["role"],
                            "PolicyArn": "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"})
            await self.call("iam", "PutRolePolicy", None, {"RoleName": NAMES["role"], "PolicyName": "read-demo-parameters",
                            "PolicyDocument": json.dumps({"Version": "2012-10-17", "Statement": [{
                                "Effect": "Allow", "Action": ["ssm:GetParametersByPath", "ssm:GetParameter"],
                                "Resource": f"arn:aws:ssm:{REGION}:*:parameter{PARAM_PATH}/*"}]})})
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

    def user_data(self):
        return f"""#!/bin/bash
set -euxo pipefail
exec > >(tee -a /var/log/aegis-bootstrap.log) 2>&1
dnf install -y docker
mkdir -p /usr/local/lib/docker/cli-plugins /opt/aegis
curl -fsSL https://github.com/docker/compose/releases/download/{COMPOSE_VERSION}/docker-compose-linux-x86_64 \\
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
systemctl enable --now docker
curl -fsSL https://codeload.github.com/{REPO}/tar.gz/{self.repo_ref} | tar -xz --strip-components=1 -C /opt/aegis
cat > /opt/aegis/refresh-env.sh <<'EOS'
#!/bin/bash
set -euo pipefail
TOKEN=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
REGION=$(curl -fsS -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/placement/region)
aws ssm get-parameters-by-path --region "$REGION" --path {PARAM_PATH} --with-decryption \\
  --query 'Parameters[].[Name,Value]' --output text | awk -F'\\t' '{{n=$1; sub(".*/","",n); print n"="$2}}' > /opt/aegis/.env
chmod 600 /opt/aegis/.env
EOS
chmod +x /opt/aegis/refresh-env.sh
cat > /etc/systemd/system/aegis-demo.service <<'EOS'
[Unit]
Description=AegisNotify demo stack
After=docker.service network-online.target
Requires=docker.service
[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/aegis
ExecStart=/bin/bash -c '/opt/aegis/refresh-env.sh && docker compose -f docker-compose.demo.yml --env-file .env up -d'
[Install]
WantedBy=multi-user.target
EOS
systemctl daemon-reload
systemctl enable --now aegis-demo.service
"""

    async def up(self):
        if not self.frontend_origin and not await self.address():
            raise SystemExit("up needs --frontend-origin (the Vercel URL, e.g. https://my-demo.vercel.app)")
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
        hosts = await self.hosts(addr)
        existing = (await self.call("ssm", "GetParametersByPath", REGION, {"Path": PARAM_PATH})).get("Parameters", [])
        if not existing:
            rnd = lambda: uuid.uuid4().hex + uuid.uuid4().hex
            for name in ("POSTGRES_PASSWORD", "KC_BOOTSTRAP_ADMIN_PASSWORD", "KEYCLOAK_ADMIN_CLIENT_SECRET",
                         "DEMO_USER_PASSWORD", "EUREKA_SECURITY_PASSWORD", "CONFIG_SERVER_PASSWORD"):
                await self.put_param(name, rnd())
            await self.put_param("AUDIT_ENCRYPTION_KEY", base64.b64encode(bytes.fromhex(uuid.uuid4().hex + uuid.uuid4().hex)).decode())
        for name, value in (("API_HOST", hosts["api"]), ("AUTH_HOST", hosts["auth"]), ("AEGIS_VERSION", self.version)):
            await self.put_param(name, value, secure=False)
        if self.frontend_origin:
            await self.put_param("FRONTEND_ORIGIN", self.frontend_origin.rstrip("/"), secure=False)
        inst = await self.instance()
        if not inst:
            ami = (await self.call("ssm", "GetParameter", REGION, {"Name": AMI_PARAM}))["Parameter"]["Value"]
            for attempt in range(8):  # a new instance profile can take a few seconds to propagate
                try:
                    run = await self.ec2(
                        "RunInstances", ImageId=ami, InstanceType=INSTANCE_TYPE, MinCount=1, MaxCount=1,
                        IamInstanceProfile={"Name": NAMES["profile"]}, SecurityGroupIds=[sg["GroupId"]],
                        CreditSpecification={"CpuCredits": "standard"},  # throttle instead of billing surplus credits
                        MetadataOptions={"HttpTokens": "required", "HttpPutResponseHopLimit": 2},
                        BlockDeviceMappings=[{"DeviceName": "/dev/xvda", "Ebs": {
                            "VolumeSize": VOLUME_GB, "VolumeType": "gp3", "Encrypted": True, "DeleteOnTermination": True}}],
                        UserData=self.user_data(),
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
        params = (await self.call("ssm", "GetParametersByPath", REGION, {"Path": PARAM_PATH})).get("Parameters", [])
        if params:
            for chunk in range(0, len(params), 10):
                await self.call("ssm", "DeleteParameters", REGION, {"Names": [p["Name"] for p in params[chunk:chunk + 10]]})
            removed.append("SSM parameters")
        await attempt("EC2 role policy", "iam", "DeleteRolePolicy", None, RoleName=NAMES["role"], PolicyName="read-demo-parameters")
        await attempt("EC2 role policy attachment", "iam", "DetachRolePolicy", None, RoleName=NAMES["role"],
                      PolicyArn="arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore")
        await attempt("profile role", "iam", "RemoveRoleFromInstanceProfile", None, InstanceProfileName=NAMES["profile"], RoleName=NAMES["role"])
        await attempt("instance profile", "iam", "DeleteInstanceProfile", None, InstanceProfileName=NAMES["profile"])
        await attempt("EC2 role", "iam", "DeleteRole", None, RoleName=NAMES["role"])
        await attempt("scheduler role policy", "iam", "DeleteRolePolicy", None, RoleName=NAMES["sched_role"], PolicyName="stop-demo-instance")
        await attempt("scheduler role", "iam", "DeleteRole", None, RoleName=NAMES["sched_role"])
        return {"removed": removed, "kept": "AWS Budget aegis-demo-credits (created separately)"}


async def main(call, action, frontend_origin=None, repo_ref="main", version="0.18.0", yes=False):
    demo = Demo(call, frontend_origin, repo_ref, version)
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
    parser.add_argument("--version", default="0.18.0", help="Image tag (AEGIS_VERSION)")
    parser.add_argument("--yes", action="store_true", help="Confirm destroy")
    args = parser.parse_args()
    print(json.dumps(asyncio.run(main(boto3_call, args.action, args.frontend_origin, args.repo_ref, args.version, args.yes)), indent=2))
