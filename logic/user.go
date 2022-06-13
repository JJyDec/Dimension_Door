package logic

import (
	"dimension_door/dao/mysql"
	"dimension_door/models"
	"dimension_door/pkg/jwt"
	"go.uber.org/zap"
)


func Login(resp *models.WXLoginResp,user *models.Usermsg)(token string,err error){
	//插入数据库
	err = mysql.LoginInsert(resp,user)

	//返回Token
	token , err = jwt.GenToken(resp.Openid)

	return
}

// InputUser 插入/更新用户信息
func InputUser(p *models.StudentMsg, userid string) error {
	// 判断userid是否已经在数据库中
	ok, err := mysql.CheckUserId(userid)
	if err != nil {
		zap.L().Error("mysql.CheckUserId() error", zap.Error(err))
		return err
	}
	if !ok {
		return mysql.InputUser(p, userid)
	}
	return mysql.UpdateUser(p, userid)
}