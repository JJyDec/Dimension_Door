package mysql

import "go.uber.org/zap"

// 和管理员相关的数据库操作

// CheckIsAdminAccount 查看是否是管理员
func CheckIsAdminAccount(openid string)(bool,error){
	var num int
	sqlStr := `select count(*) from admin where openid = ? limit 1`
	err := db.Get(&num,sqlStr,openid)
	if err != nil {
		zap.L().Error("`select count(*) from admin where openid = ?` failed",zap.Error(err))
		return false,err
	}
	if num <= 0 {
		return false,err
	}
	return true,err
}
