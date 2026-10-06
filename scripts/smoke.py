#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Verify an explicitly disposable loopback instance and independently enumerate feasible pairs."""
from pathlib import Path
import argparse, concurrent.futures, http.cookiejar, itertools, json, os, secrets, urllib.request, urllib.error, uuid
ROOT=Path(__file__).resolve().parents[1]; STATE=ROOT/'output/qa-state.json'; BASE=os.environ.get('TEST_URL','http://127.0.0.1:8133').rstrip('/'); checks=0

def check(ok,message):
    """Count assertions without printing credentials. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)
def key():return str(uuid.uuid4())
class Client:
    """Real session and CSRF handling, no authentication bypass. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf'); self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=40) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: unexpected status {actual}')
        if code:check(isinstance(value,dict) and value.get('code')==code,'Unexpected error code: '+path)
        return value
    def csv(self,id):
        with self.opener.open(BASE+f'/api/jobs/{id}/cases.csv') as res:
            check(res.status==200 and 'text/csv' in res.headers['Content-Type'],'CSV headers');return res.read().decode('utf8')

def record(id,actor=None):return (actor or admin).request(f'/jobs/{id}')
def body(id):return {'requestKey':key(),'version':record(id)['version'],'jobId':id,'note':'TEST 核对参数模型与实际测试结果'}
def command(actor,id,action,status=200,code=None,extra=None):
    """Bind commands to current persisted version. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    return actor.request(f'/jobs/{id}/commands/{action}','POST',{**body(id),**(extra or {})},status,code)
def capture():
    """Read complete stable business and identity responses. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard','/jobs?size=50&sort=oldest']
    jobs=admin.request('/jobs?size=50')['rows'];paths += [f'/jobs/{j["id"]}' for j in jobs]
    paths += [f'/revisions/{r["id"]}' for j in jobs for r in record(j['id'])['revisions']]
    return {p:admin.request(p) for p in paths}
def independent(d):
    """Independently enumerate raw configurations, feasible pairs and actual coverage. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    p=d['plan'];fs=p['factors'];allowed=[];pair_universe=set()
    def pairs(row):return {(fs[i]['code'],row[i],fs[j]['code'],row[j]) for i in range(len(fs)) for j in range(i+1,len(fs))}
    for row in itertools.product(*(f['values'] for f in fs)):
        cfg=dict(zip((f['code'] for f in fs),row))
        if any(cfg[r['leftFactor']]==r['leftValue'] and cfg[r['rightFactor']]==r['rightValue'] for r in p['constraints']):continue
        allowed.append(row);pair_universe |= pairs(row)
    check(len(allowed)==p['validConfigurations'],'Valid count mismatch')
    raw=1
    for f in fs:raw*=len(f['values'])
    check(raw==p['totalConfigurations'] and raw-len(allowed)==p['rejectedConfigurations'],'Raw space mismatch')
    listed=[(x['leftFactor'],x['leftValue'],x['rightFactor'],x['rightValue']) for x in p['pairs']]
    check(len(listed)==len(set(listed)) and set(listed)==pair_universe,'Feasible universe mismatch')
    planned=set();executed=set();passing=set();failing=set();seen=set(); counts={'PASS':0,'FAIL':0,'BLOCKED':0};rs={r['caseNumber']:r for r in d['results']}
    for number,c in enumerate(p['cases'],1):
        check(c['number']==number,'Nonsequential case');check([s['factor'] for s in c['selections']]==[f['code'] for f in fs],'Factor order mismatch')
        row=tuple(s['value'] for s in c['selections']);check(row in allowed and row not in seen,'Invalid or duplicate case');seen.add(row);case_pairs=pairs(row);planned |= case_pairs
        check({listed[i] for i in c['pairIndexes']}==case_pairs,'Case indexes mismatch')
        r=rs.get(number)
        if r:
            check(r['status'] in counts,'Invalid actual verdict');counts[r['status']]+=1
            if r['status'] in ['PASS','FAIL']:executed |= case_pairs
            if r['status']=='PASS':passing |= case_pairs
            if r['status']=='FAIL':failing |= case_pairs
    check(planned==pair_universe,'Generated coverage incomplete');c=d['coverage']
    for k,v in [('cases',len(p['cases'])),('passed',counts['PASS']),('failed',counts['FAIL']),('blocked',counts['BLOCKED']),('pending',len(p['cases'])-len(rs)),('feasiblePairs',len(pair_universe)),('executedPairs',len(executed)),('passedPairs',len(passing)),('failedPairs',len(failing))]:check(c[k]==v,'Actual coverage mismatch '+k)
    check({(x['leftFactor'],x['leftValue'],x['rightFactor'],x['rightValue']) for x in c['unexecutedPairs']}==pair_universe-executed,'Missing pair mismatch')
    unreachable={(f['code'],v) for i,f in enumerate(fs) for v in f['values'] if not any(row[i]==v for row in allowed)}
    check({(v['factor'],v['value']) for v in p['unreachableValues']}==unreachable,'Unreachable value mismatch')

parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--capture',action='store_true');parser.add_argument('--verify',action='store_true');args=parser.parse_args()
check(BASE.startswith(('http://127.0.0.1:','http://localhost:')),'Use an isolated loopback instance')
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
    saved=json.loads(STATE.read_text());responses=capture()
    if args.capture:
        saved['responses']=responses;STATE.write_text(json.dumps(saved,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(responses),'result':'PASS'}));raise SystemExit
    for path,expected in saved['responses'].items():check(responses[path]==expected,'Persistence mismatch: '+path)
    for name,username in saved['users'].items():check(Client(username,saved['password']).request('/auth/me')['username']==username,'Actor missing: '+name)
    for r in saved['replays']:
        actor=Client(saved['users'][r['actor']],saved['password']);check(actor.request(r['path'],'POST',r['body'])==r['response'],'Cached response changed')
    print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(responses),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only on an isolated disposable database.')
if STATE.exists():raise SystemExit('Existing QA state; verify it or use a fresh disposable database.')
check(admin.request('/jobs')['total']==0,'First-start business must be empty')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24);users={};clients={};ids={};jobs=[];replays=[]
roles={r['name']:r['id'] for r in admin.request('/admin/roles')};dep=admin.request('/admin/departments','POST',{'name':'TEST 配置兼容性 '+suffix})['id'];external=admin.request('/admin/departments','POST',{'name':'TEST 外部 '+suffix})['id']
wide=admin.request('/admin/roles','POST',{'name':'TEST 执行ALL '+suffix,'scope':'ALL','permissions':['job.read','test.write','dashboard','export']})['id']
for name,role,department,label in [('planner',roles['方案设计'],dep,'参数设计'),('review',roles['独立复核'],dep,'独立复核'),('operator',wide,dep,'测试执行'),('second',roles['方案设计'],dep,'共同编辑'),('viewer',roles['部门查阅'],dep,'部门查阅'),('outside',roles['方案设计'],external,'外部设计')]:
    username='test-'+name+'-'+suffix;users[name]=username
    ids[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+label,'password':password,'roleId':role,'departmentId':department,'enabled':True})['id'];clients[name]=Client(username,password)
planner,review,operator=[clients[n] for n in ['planner','review','operator']]
def draft():
    """Create labelled test fixtures with explicit values; no production claim. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    b={'requestKey':key(),'reference':'TEST-CFG-'+key()[:8],'name':'TEST 多环境Web兼容性复核','departmentId':dep,'category':'WEB','systemName':'TEST 隔离登录与权限页面','buildLabel':'TEST-build-0.1','environment':'TEST 独立回环环境／手工兼容性用例','instructions':'TEST 核对登录、访问范围与中文／英文显示；按每条用例记录实际通过、失败或阻塞，不能把未执行计为通过。','reviewerId':ids['review'],'operatorId':ids['operator']}
    j=planner.request('/jobs','POST',b);id=j['id'];jobs.append(id);check(planner.request('/jobs','POST',b)==j,'Create retry changed')
    for code,name,values in [('BROWSER','浏览器',['Chrome','Edge','Firefox']),('AUTH','认证方式',['Password','SSO']),('LANG','界面语言',['zh-CN','en-US'])]:
        planner.request('/factors','POST',{'requestKey':key(),'version':record(id)['version'],'jobId':id,'code':code,'name':name,'values':values})
    return id
def add_rule(id,a,av,b,bv):
    fs={f['code']:f['id'] for f in record(id)['factors']};return planner.request('/constraints','POST',{'requestKey':key(),'version':record(id)['version'],'jobId':id,'leftFactorId':fs[a],'leftValue':av,'rightFactorId':fs[b],'rightValue':bv,'reason':'TEST 明确排除的参数组合'})
def running(id):
    """Generate, independently verify and seal a stored plan. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    command(planner,id,'generate');independent(record(id));command(planner,id,'submit');b=body(id);path=f'/jobs/{id}/commands/approve';r=review.request(path,'POST',b);check(review.request(path,'POST',b)==r,'Approval retry changed');replays.append({'actor':'review','path':path,'body':b,'response':r});review.request(path,'POST',{**b,'note':'TEST changed'},409,'REQUEST_KEY_REUSED');command(planner,id,'start')
def actual_body(id,case,status='PASS'):
    return {'requestKey':key(),'version':record(id)['version'],'jobId':id,'caseNumber':case,'status':status,'note':'TEST 明确人工测试声明','evidence':'TEST 受限验收记录'}
def fill(id,first='PASS'):
    """Declare every test verdict explicitly in disposable fixtures. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    for c in record(id)['plan']['cases']:
        b=actual_body(id,c['number'],first if c['number']==1 else 'PASS');r=operator.request('/results','POST',b);check(operator.request('/results','POST',b)==r,'Result retry changed')
    independent(record(id))
def seal(id,outcome='FINISHED'):
    command(operator,id,'submit-report',extra={'outcome':outcome});command(review,id,'close');check(len(record(id)['closedHash'])==64,'No closed digest')

j1=draft();running(j1);command(planner,j1,'approve',403,'FORBIDDEN');command(operator,j1,'submit-report',409,'ACKNOWLEDGEMENT_REQUIRED',{'outcome':'FINISHED'});command(operator,j1,'acknowledge');command(operator,j1,'submit-report',409,'INCOMPLETE_RESULT',{'outcome':'FINISHED'});fill(j1);command(operator,j1,'submit-report',extra={'outcome':'FINISHED'});command(review,j1,'return-result');fill(j1);seal(j1);check(record(j1)['outcome']=='PASSED','Pass outcome mismatch')
j2=draft();add_rule(j2,'BROWSER','Firefox','AUTH','SSO');running(j2);command(operator,j2,'acknowledge');fill(j2,'FAIL');seal(j2);check(record(j2)['outcome']=='ISSUES','Failure hidden');check(record(j2)['coverage']['executedPercent']==100,'Execution differs from passing coverage')
j3=draft();running(j3);command(operator,j3,'acknowledge');fill(j3,'BLOCKED');command(operator,j3,'submit-report',409,'OUTCOME_MISMATCH',{'outcome':'FINISHED'});seal(j3,'PARTIAL');check(record(j3)['outcome']=='PARTIAL' and record(j3)['coverage']['executedPercent']<100,'Blocker hidden')
j4=draft();add_rule(j4,'BROWSER','Firefox','AUTH','Password');add_rule(j4,'BROWSER','Firefox','AUTH','SSO');command(planner,j4,'generate');independent(record(j4));check(record(j4)['plan']['unreachableValues']==[{'factor':'BROWSER','value':'Firefox'}],'Unreachable value hidden');command(planner,j4,'cancel')
j5=draft();add_rule(j5,'BROWSER','Firefox','AUTH','SSO');command(planner,j5,'generate');command(planner,j5,'submit');command(review,j5,'return-plan');planner.request('/factors','POST',{'requestKey':key(),'version':record(j5)['version'],'jobId':j5,'code':'DEVICE','name':'访问终端','values':['Desktop','Tablet']});command(planner,j5,'generate');independent(record(j5));check(len(record(j5)['revisions'])==2,'History missing')
j6=draft();running(j6);command(operator,j6,'acknowledge');fill(j6)
j7=draft();d=record(j7);f=d['factors'][0];b={'requestKey':key(),'version':d['version'],'jobId':j7,'code':'BROWSER','name':f['name'],'values':['same','same']};planner.request('/factors/'+str(f['id']),'PUT',b,400,'INVALID_VALUES');b['values']=['valid','other'];b['status']='CLOSED';planner.request('/factors/'+str(f['id']),'PUT',b,400,'INVALID_INPUT')
for a in [clients['outside']]:
    a.request(f'/jobs/{j1}',status=403,code='OUT_OF_SCOPE');a.request(f'/jobs/{j1}/report.json',status=403,code='OUT_OF_SCOPE');a.request(f'/revisions/{record(j1)["activeRevisionId"]}',status=403,code='OUT_OF_SCOPE');check(a.request('/jobs')['total']==0,'Outside list leak')
operator.request('/admin/users',status=403,code='FORBIDDEN');clients['viewer'].request(f'/jobs/{j5}/commands/generate','POST',body(j5),403,'FORBIDDEN');planner.request('/jobs?size=51',status=400,code='INVALID_INPUT');planner.request('/jobs?sort=sql',status=400,code='INVALID_INPUT');planner.request('/jobs','POST',{},403,csrf=False)
# Revocation precedes a successful cached result.
b=actual_body(j6,1);r=operator.request('/results','POST',b);role=next(x for x in admin.request('/admin/roles') if x['id']==wide);finite={k:role[k] for k in ['name','scope','permissions']};admin.request('/admin/roles/'+str(wide),'PUT',{**finite,'permissions':['job.read','dashboard','export']});operator.request('/results','POST',b,403,'FORBIDDEN');admin.request('/admin/roles/'+str(wide),'PUT',finite);check(operator.request('/results','POST',b)==r,'Restored replay mismatch')
# Two same-version generators produce exactly one new revision.
b=body(j5);actors=[Client(users['planner'],password),Client(users['planner'],password)];before=len(record(j5)['revisions'])
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:answers=list(pool.map(lambda pair:pair[0].request(f'/jobs/{j5}/commands/generate','POST',{**b,'requestKey':pair[1]},status=(200,409)),zip(actors,[key(),key()])))
check(sum('id' in a for a in answers)==1,'Concurrent mutation had no unique winner');check(len(record(j5)['revisions'])==before+1,'Concurrent duplicate revision')
# Identity directories are real and leave the sole all-scope administrator available.
x=next(a for a in admin.request('/admin/users') if a['id']==ids['outside']);finite={k:x[k] for k in ['username','displayName','roleId','departmentId','enabled']};admin.request('/admin/users/'+str(x['id']),'PUT',{**finite,'enabled':False});clients['outside'].request('/auth/me',status=401,code='UNAUTHENTICATED');admin.request('/admin/users/'+str(x['id']),'PUT',finite)
a=next(x for x in admin.request('/admin/users') if x['username']=='admin');finite={k:a[k] for k in ['username','displayName','roleId','departmentId','enabled']};admin.request('/admin/users/'+str(a['id']),'PUT',{**finite,'enabled':False},409,'LAST_ADMIN');admin.request('/admin/departments/'+str(dep),'DELETE',status=409,code='CONFLICT')
temporary=admin.request('/admin/departments','POST',{'name':'TEST 未引用 '+suffix});admin.request('/admin/departments/'+str(temporary['id']),'PUT',{'name':'TEST 已编辑 '+suffix});admin.request('/admin/departments/'+str(temporary['id']),'DELETE')
setting=next(s for s in admin.request('/admin/settings') if s['code']=='companyName');admin.request('/admin/settings/'+str(setting['id']),'PUT',{'value':'ConfigPair · TEST 兼容性工作空间'})
for id in [j1,j2,j3,j4,j5,j6]:independent(record(id));check(admin.csv(id).startswith('\ufeffcase_number'),'CSV BOM missing')
state={'password':password,'users':users,'ids':ids,'jobs':jobs,'replays':replays,'responses':capture()};STATE.parent.mkdir(exist_ok=True);fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out,ensure_ascii=False)
print(json.dumps({'result':'PASS','assertions':checks,'jobs':len(jobs),'independentFeasibleCoverage':'PASS','privateStateMode':'0600'}))
