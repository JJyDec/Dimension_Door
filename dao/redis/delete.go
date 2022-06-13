package redis

// redis中删除相关的操作

// DeletePostByID 根据帖子id删除redis中内容
func DeletePostByID(pid string)error{
	pipeline := rdb.TxPipeline()
	//删除投票记录
	pipeline.Del(getRedisKey(KeyPostVotedZsetPF + pid))
	// 删除时间榜记录
	pipeline.ZRem(getRedisKey(KeyPostScoreZset),pid)
	//删除分数榜记录
	pipeline.ZRem(getRedisKey(KeyPostTimeZset),pid)
	_,err := pipeline.Exec()
	return err
}

// RemApplyKey 移除redis中用户报名的记录
func RemApplyKey(postid int64,userid string){
	key := getRedisKey(KeyLectureApplicationZsetPF + userid)
	rdb.SRem(key,postid)
}
