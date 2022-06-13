package mysql

import (
	"go.uber.org/zap"
)

//和报名相关的数据库操作


// ApplicationInsert 向报名表插入报名者信息，讲座id等
func ApplicationInsert(postid int64,openid string)error{
	sqlStr := `insert into application(openid,post_id) values(?,?)`
	_,err := db.Exec(sqlStr,openid,postid)
	return err
}

// CheckApplyByID 查询是否已经有该用户记录
func CheckApplyByID(postid int64,openid string)(bool,error){
	var nums int
	sqlStr := `select count(*) from application where post_id = ? and openid = ? limit 1`
	err := db.Get(&nums,sqlStr,postid,openid)
	if nums == 0 {
		return true,err
	}
	return false,err
}

// GetPostedNumsByID 根据讲座id获取有多少人报名这个讲座
func GetPostedNumsByID(pid int64)(int64,error){
	var nums int64
	sqlStr := `select count(*) from application where post_id = ?`
	err := db.Get(&nums,sqlStr,pid)
	return nums , err
}

// CancelApplyByID 根据讲座id取消报名
func CancelApplyByID(pid int64 ,openid string)error{
	tx,err := db.Begin()
	if err != nil {
		if tx != nil{
			tx.Rollback()
		}
		zap.L().Error("db.Begin() failed ,err",zap.Error(err))
		return err
	}
	lock.Lock()
	sqlStr1 := `delete from application where post_id = ? and openid = ?`
	ref1,err := tx.Exec(sqlStr1,pid,openid)
	if err != nil {
		zap.L().Error("`delete from application where post_id = ? and openid = ?` failed err:",zap.Error(err))
		tx.Rollback()
		lock.Unlock()
		return err
	}
	affref1,err:= ref1.RowsAffected()
	if err != nil {
		zap.L().Error("`ref1.RowsAffected()",zap.Error(err))
		tx.Rollback()
		lock.Unlock()
		return err
	}
	sqlStr2 := `update post set posted_nums=posted_nums-1 where post_id = ?`
	ref2,err := tx.Exec(sqlStr2,pid)
	if err != nil {
		zap.L().Error("`update post set posted_nums=posted_nums+1 where post_id = ?` failed err:",zap.Error(err))
		tx.Rollback()
		lock.Unlock()
		return err
	}
	affref2,err := ref2.RowsAffected()
	if err != nil {
		zap.L().Error("`ref2.RowsAffected()",zap.Error(err))
		tx.Rollback()
		lock.Unlock()
		return err
	}
	if affref2 == 1 && affref1 == 1{
		tx.Commit()
	}else {
		tx.Rollback()
	}
	lock.Unlock()
	return err
}