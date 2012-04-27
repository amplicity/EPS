--issue 8 March2012
delete t1 from dbeps01.teb_dictionary t1, dbeps01.teb_dictionary t2 where  t1.stKeyword=t2.stKeyword and t1.RecId!=t2.RecId and ((t1.stKeywordType=t2.stKeywordType and t1.RecId>t2.RecId) OR (t1.stKeywordType='N' and t2.stKeywordType!='N') OR (t1.stKeywordType='V' and t2.stKeywordType!='N' and t2.stKeywordType!='V') OR t1.RecId=278730)


--issue 52 March2012
ALTER TABLE `dbeps01`.`schedule` CHANGE `SchFlags` `SchFlags` VARCHAR( 8 ) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '0';
UPDATE `dbeps01`.`schedule` SET `SchFlags`='0' WHERE `SchFlags`='';


--issue 55 April2012
UPDATE `dbeps01`.`teb_fields` SET `stDbFieldName` = 'UserDivision' WHERE `teb_fields`.`nmForeignId` =42;
ALTER TABLE `dbeps01`.`users` ADD `UserLaborCategory` VARCHAR( 10 ) NOT NULL DEFAULT '' AFTER `UserDivision`;
UPDATE `dbeps01`.`teb_fields` SET `stDbFieldName` = 'UserLaborCategory' WHERE `teb_fields`.`nmForeignId` =16;

--issue 35 April2012
UPDATE `ebeps01`.`x25user` SET `stPassword`='*3B1FD1671E1353A14FA2B20E97F70A4BB1EF0DBB' WHERE `stPassword`='*3E1DFA47500CCB1882BD912C951259C5A842B17F'


--issue 73,75
UPDATE `dbeps01`.`teb_fields` SET `nmDataType` = '9' WHERE `teb_fields`.`nmForeignId` =231 OR `teb_fields`.`nmForeignId` =271;

--issue 36
INSERT INTO `dbeps01`.`teb_reportcolumns` (
`nmCustomReportId` ,`nmFieldId` ,`stShow` ,`nmOrder` ,`stShort` ,`nmWidth` ,`stClass` ,`stCustom`
)
VALUES ('11', '873', 'Y', '8', '', '10', 'null', ''),('11', '884', 'Y', '9'', '', '3', 'null', ''),('11', '883', 'Y', '10', '', '20', 'null', '');

