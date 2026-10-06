-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
create table pair_job (
 id bigint auto_increment primary key,
 reference varchar(60) not null,
 name varchar(160) not null,
 department_id bigint not null,
 category varchar(60) not null,
 system_name varchar(160) not null,
 build_label varchar(160) not null,
 environment varchar(1000) not null,
 instructions varchar(1000) not null,
 reviewer_id bigint not null,
 operator_id bigint not null,
 created_by bigint not null,
 status varchar(30) not null,
 active_revision_id bigint null,
 approved_hash varchar(64) null,
 report_hash varchar(64) null,
 closed_hash varchar(64) null,
 requested_outcome varchar(30) null,
 outcome varchar(30) null,
 acknowledged_at timestamp(6) null,
 approved_at timestamp(6) null,
 closed_at timestamp(6) null,
 created_at timestamp(6) not null,
 version bigint not null,
 UNIQUE(reference),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(reviewer_id) REFERENCES account(id),
 FOREIGN KEY(operator_id) REFERENCES account(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 INDEX ix_pair_scope(department_id,status)
);
create table pair_factor (
 id bigint auto_increment primary key,
 job_id bigint not null,
 code varchar(30) not null,
 name varchar(160) not null,
 values_json longtext not null,
 FOREIGN KEY(job_id) REFERENCES pair_job(id),
 UNIQUE(job_id,code),
 UNIQUE(id,job_id)
);
create table pair_constraint (
 id bigint auto_increment primary key,
 job_id bigint not null,
 left_factor_id bigint not null,
 left_value varchar(60) not null,
 right_factor_id bigint not null,
 right_value varchar(60) not null,
 reason varchar(1000) not null,
 FOREIGN KEY(job_id) REFERENCES pair_job(id),
 FOREIGN KEY(left_factor_id,job_id) REFERENCES pair_factor(id,job_id),
 FOREIGN KEY(right_factor_id,job_id) REFERENCES pair_factor(id,job_id),
 CHECK(left_factor_id <> right_factor_id),
 UNIQUE(job_id,left_factor_id,left_value,right_factor_id,right_value)
);
create table pair_revision (
 id bigint auto_increment primary key,
 job_id bigint not null,
 algorithm varchar(80) not null,
 input_hash varchar(64) not null,
 plan_hash varchar(64) not null,
 input_json longtext not null,
 plan_json longtext not null,
 created_by bigint not null,
 created_at timestamp(6) not null,
 FOREIGN KEY(job_id) REFERENCES pair_job(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 UNIQUE(id,job_id),
 INDEX ix_pair_revision(job_id,id)
);
create table pair_editor (
 id bigint auto_increment primary key,
 job_id bigint not null,
 actor_id bigint not null,
 FOREIGN KEY(job_id) REFERENCES pair_job(id),
 FOREIGN KEY(actor_id) REFERENCES account(id),
 UNIQUE(job_id,actor_id)
);
create table pair_result (
 id bigint auto_increment primary key,
 job_id bigint not null,
 revision_id bigint not null,
 case_number int not null,
 status varchar(30) not null,
 note varchar(1000) not null,
 evidence varchar(1000) not null,
 actor_id bigint not null,
 recorded_at timestamp(6) not null,
 FOREIGN KEY(job_id) REFERENCES pair_job(id),
 FOREIGN KEY(revision_id,job_id) REFERENCES pair_revision(id,job_id),
 FOREIGN KEY(actor_id) REFERENCES account(id),
 UNIQUE(job_id,revision_id,case_number),
 CHECK(case_number BETWEEN 1 AND 256),
 CHECK(status IN ('PASS','FAIL','BLOCKED'))
);
alter table pair_job add constraint fk_pair_active foreign key(active_revision_id,id) references pair_revision(id,job_id);
create table command_record (id bigint auto_increment primary key,request_key varchar(36) not null unique,fingerprint varchar(64) not null,response_json longtext not null);
create table business_event (id bigint auto_increment primary key,object_type varchar(30) not null,object_id bigint not null,actor_id bigint not null,action varchar(60) not null,note varchar(1000) not null,snapshot longtext not null,created_at timestamp(6) not null);
alter table business_event add constraint fk_business_event_actor_id foreign key (actor_id) references account(id);
create index idx_event_obj on business_event(object_type,object_id);
