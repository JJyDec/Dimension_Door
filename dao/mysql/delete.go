package mysql

//跟删除有关的数据库操作

// GetTeacherIDByPostID 根据帖子id得到教师ID
func GetTeacherIDByPostID(pid int64)(tid int64,err error){
	sqlStr := `select teacher_id from post where post_id = ?`
	err = db.Get(&tid,sqlStr,pid)
	return
}

// DeletePostByID 删除讲座
func DeletePostByID(pid  int64)error{
	sqlStr := `delete from post where post_id = ?`
	_,err := db.Exec(sqlStr,pid)
	return err
}