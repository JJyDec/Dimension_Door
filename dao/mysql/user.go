package mysql

import "dimension_door/models"

// LoginInsert 插入加密后的sessionkey 和用户openid和session
func LoginInsert(resp *models.WXLoginResp,user *models.Usermsg)(error){
	//sql语句
	sqlStr := `insert into user(openid,username,picture,gender) values(?,?,?,?)`
	_,err := db.Exec(sqlStr,resp.Openid,user.Username,user.Picture,user.Gender)
	return err
}

// InputUser 插入一条用户信息
func InputUser(p *models.StudentMsg, userid string) error {
	// sql语句
	sqlstr := `insert into student_id(openid, student_id, name, class) values(?, ?, ?, ?)`
	_, err := db.Exec(sqlstr, userid, p.StudentId, p.Name, p.Class)
	return err
}

// CheckUserId 判断数据库中是否存在userid
func CheckUserId(userid string) (bool, error) {
	var number int
	sqlstr := `select count(*) from student_id where openid = ? limit 1`
	err := db.Get(&number, sqlstr, userid)
	if number <= 0 {
		return false, err
	}
	return true, err
}

// UpdateUser 更新一条用户信息
func UpdateUser(p *models.StudentMsg, userid string) error {
	sqlstr := "update student_id set student_id = ? , name = ?, class = ? where openid = ?"
	_, err := db.Exec(sqlstr, p.StudentId, p.Name, p.Class, userid)
	return err
}