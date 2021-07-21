create or replace procedure CREATE_SCHEMA(p_username IN varchar2, p_password IN varchar2)
IS
    stmt varchar2(5000);
begin
    stmt := 'create user '||p_username||' identified by '|| p_password ||' profile DEFAULT';
    execute immediate stmt;
    stmt := 'grant connect to '||p_username;
    execute immediate stmt;
    stmt := 'grant developer to '||p_username;
    execute immediate stmt;
    stmt := 'grant select_catalog_role to '||p_username;
    execute immediate stmt;
    stmt := 'grant create any procedure to '||p_username;
    execute immediate stmt;
    stmt := 'grant create any trigger to '||p_username;
    execute immediate stmt;
    stmt := 'grant create sequence to '||p_username;
    execute immediate stmt;
    stmt := 'grant create session to '||p_username;
    execute immediate stmt;
    stmt := 'grant create synonym to '||p_username;
    execute immediate stmt;
    stmt := 'grant create table to '||p_username;
    execute immediate stmt;
    stmt := 'grant create view to '||p_username;
    execute immediate stmt;
    stmt := 'grant select any table to '||p_username;
    execute immediate stmt;
    stmt := 'grant unlimited tablespace to '||p_username;
    execute immediate stmt;
    dbms_output.put_line ('user '||p_username||' created');
end;
/

create or replace procedure DROP_SCHEMA(p_username IN varchar2)
IS
    stmt varchar2(5000);
begin
    for i in (select sid, serial# from v$session where lower(username)=lower(p_username))
        loop
            stmt := 'alter system kill session ''' || i.sid || ',' || i.serial#  || ''' immediate';
            execute immediate stmt;
        end loop;

    dbms_output.put_line ('sessions of user '||lower(p_username)||' killed');

    stmt := 'drop user '||lower(p_username)||' cascade';
    execute immediate stmt;
    dbms_output.put_line ('user '||lower(p_username)||' drop');
end;
/
